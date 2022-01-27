// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.notification;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.notification.impl.NotificationCollector;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.notification.impl.NotificationsToolWindowFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.*;
import com.intellij.ui.BalloonLayoutData;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.content.Content;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public final class EventLog {
  public static final String LOG_REQUESTOR = "Internal log requestor";
  public static final String LOG_TOOL_WINDOW_ID = "Event Log";
  public static final String HELP_ID = "reference.toolwindows.event.log";
  private static final String A_CLOSING = "</a>";
  private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]*>");
  private static final Pattern A_PATTERN = Pattern.compile("<a ([^>]* )?href=[\"']([^>]*)[\"'][^>]*>");
  @NonNls private static final Set<String> NEW_LINES = ContainerUtil.newHashSet("<br>", "</br>", "<br/>", "<p>", "</p>", "<p/>", "<pre>", "</pre>", "<li>");
  private static final String DEFAULT_CATEGORY = "";

  private final LogModel myModel = new LogModel(null);

  public static void expireNotifications() {
    for (Notification notification : getApplicationService().myModel.getNotifications()) {
      notification.expire();
    }

    for (Project project : ProjectUtil.getOpenProjects()) {
      if (!project.isDisposed()) {
        ProjectTracker service = getProjectService(project);
        for (Notification notification : service.myProjectModel.getNotifications()) {
          notification.expire();
        }
        for (Notification notification : service.myInitial) {
          notification.expire();
        }
        service.myInitial.clear();
      }
    }
  }

  public static void expireNotification(@NotNull Notification notification) {
    getApplicationService().myModel.removeNotification(notification);
    for (Project p : ProjectUtil.getOpenProjects()) {
      if (!p.isDisposed()) {
        getProjectService(p).myProjectModel.removeNotification(notification);
      }
    }
  }

  public static void showNotification(@NotNull Project project, @NotNull String groupId, @NotNull List<String> ids) {
    getProjectService(project).showNotification(groupId, ids);
  }

  private static EventLog getApplicationService() {
    return ApplicationManager.getApplication().getService(EventLog.class);
  }

  public static @NotNull LogModel getLogModel(@Nullable Project project) {
    return project != null ? getProjectService(project).myProjectModel : getApplicationService().myModel;
  }

  public static @NotNull List<Notification> getNotifications(@NotNull Project project) {
    ProjectTracker service = project.getServiceIfCreated(ProjectTracker.class);
    return service == null ? Collections.emptyList() : service.myProjectModel.getNotifications();
  }

  public static void markAllAsRead(@Nullable Project project) {
    LogModel model = getLogModel(project);
    Set<String> groups = new HashSet<>();
    for (Notification notification : model.getNotifications()) {
      groups.add(notification.getGroupId());
      model.removeNotification(notification);
      notification.expire();
    }

    if (project != null && !groups.isEmpty()) {
      clearNMore(project, groups);
    }
  }

  public static void clearNMore(@NotNull Project project, @NotNull Collection<String> groups) {
    getProjectService(project).clearNMore(groups);
  }

  public static boolean isClearAvailable(@NotNull Project project) {
    return getProjectService(project).isClearAvailable();
  }

  public static void doClear(@NotNull Project project) {
    getProjectService(project).doClear();
  }

  public static @Nullable Trinity<Notification, @NlsContexts.StatusBarText String, Long> getStatusMessage(@Nullable Project project) {
    return getLogModel(project).getStatusMessage();
  }

  public static LogEntry formatForLog(final @NotNull Notification notification, final String indent) {
    DocumentImpl logDoc = new DocumentImpl("",true);
    AtomicBoolean showMore = new AtomicBoolean(false);
    Map<RangeMarker, HyperlinkInfo> links = new LinkedHashMap<>();
    List<RangeMarker> lineSeparators = new ArrayList<>();

    String title = notification.getTitle();
    String subtitle = notification.getSubtitle();
    if (StringUtil.isNotEmpty(title) && StringUtil.isNotEmpty(subtitle)) {
      title += " (" + subtitle + ")";
    }
    title = truncateLongString(showMore, title);
    String content = truncateLongString(showMore, notification.getContent());

    RangeMarker afterTitle = null;
    boolean hasHtml = parseHtmlContent(addIndents(title, indent), notification, logDoc, showMore, links, lineSeparators);
    if (StringUtil.isNotEmpty(title)) {
      if (StringUtil.isNotEmpty(content)) {
        appendText(logDoc, ": ");
        afterTitle = logDoc.createRangeMarker(logDoc.getTextLength() - 2, logDoc.getTextLength());
      }
    }
    int titleLength = logDoc.getTextLength();

    hasHtml |= parseHtmlContent(addIndents(content, indent), notification, logDoc, showMore, links, lineSeparators);

    List<AnAction> actions = notification.getActions();
    if (!actions.isEmpty()) {
      String text = "<p>" + StringUtil.join(actions, new Function<>() {
        private int index;

        @Override
        public String fun(AnAction action) {
          return "<a href=\"" + index++ + "\">" + action.getTemplatePresentation().getText() + "</a>";
        }
      }, isLongLine(actions) ? "<br>" : "&nbsp;&nbsp;&nbsp;") + "</p>";
      //noinspection UnresolvedPluginConfigReference
      Notification n = new Notification("", ".", NotificationType.INFORMATION).setListener(new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification n, @NotNull HyperlinkEvent event) {
          Object source = event.getSource();
          DataContext context = source instanceof Component ? DataManager.getInstance().getDataContext((Component)source) : null;
          AnAction action = notification.getActions().get(Integer.parseInt(event.getDescription()));
          Project project = null;
          if (context != null) {
            project = context.getData(CommonDataKeys.PROJECT);
          }
          NotificationCollector.getInstance()
            .logNotificationActionInvoked(project, notification, action, NotificationCollector.NotificationPlace.EVENT_LOG);
          Notification.fire(notification, action, context);
        }
      });
      if (title.length() > 0 || content.length() > 0) {
        lineSeparators.add(logDoc.createRangeMarker(TextRange.from(logDoc.getTextLength(), 0)));
      }
      hasHtml |= parseHtmlContent(text, n, logDoc, showMore, links, lineSeparators);
    }

    String status = getStatusText(logDoc, showMore, lineSeparators, indent, hasHtml);

    indentNewLines(logDoc, lineSeparators, afterTitle, hasHtml, indent);

    List<Pair<TextRange, HyperlinkInfo>> list = new ArrayList<>();
    for (RangeMarker marker : links.keySet()) {
      if (!marker.isValid()) {
        showMore.set(true);
        continue;
      }
      list.add(Pair.create(new TextRange(marker.getStartOffset(), marker.getEndOffset()), links.get(marker)));
    }

    if (showMore.get()) {
      String sb = IdeBundle.message("tooltip.event.log.show.balloon");
      if (!logDoc.getText().endsWith(" ")) {
        appendText(logDoc, " ");
      }
      appendText(logDoc, "(" + sb + ")");
      list.add(new Pair<>(TextRange.from(logDoc.getTextLength() - 1 - sb.length(), sb.length()),
                          new ShowBalloon(notification)));
    }

    return new LogEntry(logDoc.getText(), status, list, titleLength);
  }

  private static @NotNull String addIndents(@NotNull String text, @NotNull String indent) {
    return StringUtil.replace(text, "\n", "\n" + indent);
  }

  private static boolean isLongLine(@NotNull List<? extends AnAction> actions) {
    int size = actions.size();
    if (size > 3) {
      return true;
    }
    if (size > 1) {
      int length = 0;
      for (AnAction action : actions) {
        length += StringUtil.length(action.getTemplatePresentation().getText());
      }
      return length > 30;
    }
    return false;
  }

  private static @NotNull String truncateLongString(AtomicBoolean showMore, String title) {
    if (title.length() > 1000) {
      showMore.set(true);
      return title.substring(0, 1000) + "...";
    }
    return title;
  }

  private static void indentNewLines(DocumentImpl logDoc, List<RangeMarker> lineSeparators, RangeMarker afterTitle, boolean hasHtml, String indent) {
    if (!hasHtml) {
      int i = -1;
      while (true) {
        i = StringUtil.indexOf(logDoc.getText(), '\n', i + 1);
        if (i < 0) {
          break;
        }
        lineSeparators.add(logDoc.createRangeMarker(i, i + 1));
      }
    }
    if (!lineSeparators.isEmpty() && afterTitle != null && afterTitle.isValid()) {
      lineSeparators.add(afterTitle);
    }
    int nextLineStart = -1;
    for (RangeMarker separator : lineSeparators) {
      if (separator.isValid()) {
        int start = separator.getStartOffset();
        if (start == nextLineStart) {
          continue;
        }

        logDoc.replaceString(start, separator.getEndOffset(), "\n" + indent);
        nextLineStart = start + 1 + indent.length();
        while (nextLineStart < logDoc.getTextLength() && Character.isWhitespace(logDoc.getCharsSequence().charAt(nextLineStart))) {
          logDoc.deleteString(nextLineStart, nextLineStart + 1);
        }
      }
    }
  }

  private static @Nls String getStatusText(DocumentImpl logDoc,
                                      AtomicBoolean showMore,
                                      List<? extends RangeMarker> lineSeparators,
                                      String indent,
                                      boolean hasHtml) {
    DocumentImpl statusDoc = new DocumentImpl(logDoc.getImmutableCharSequence(), true);
    List<RangeMarker> statusSeparators = new ArrayList<>();
    for (RangeMarker separator : lineSeparators) {
      if (separator.isValid()) {
        statusSeparators.add(statusDoc.createRangeMarker(separator.getStartOffset(), separator.getEndOffset()));
      }
    }
    removeJavaNewLines(statusDoc, statusSeparators, indent, hasHtml);
    insertNewLineSubstitutors(statusDoc, showMore, statusSeparators);

    return statusDoc.getText();
  }

  private static boolean parseHtmlContent(String text, Notification notification,
                                          Document document,
                                          AtomicBoolean showMore,
                                          Map<RangeMarker, HyperlinkInfo> links, List<RangeMarker> lineSeparators) {
    String content = StringUtil.convertLineSeparators(text);

    int initialLen = document.getTextLength();
    boolean hasHtml = false;
    while (true) {
      Matcher tagMatcher = TAG_PATTERN.matcher(content);
      if (!tagMatcher.find()) {
        appendText(document, content);
        break;
      }

      String tagStart = tagMatcher.group();
      appendText(document, content.substring(0, tagMatcher.start()));
      Matcher aMatcher = A_PATTERN.matcher(tagStart);
      if (aMatcher.matches()) {
        final String href = aMatcher.group(2);
        int linkEnd = content.indexOf(A_CLOSING, tagMatcher.end());
        if (linkEnd > 0) {
          String linkText = content.substring(tagMatcher.end(), linkEnd).replaceAll(TAG_PATTERN.pattern(), "");
          int linkStart = document.getTextLength();
          appendText(document, linkText);
          links.put(document.createRangeMarker(linkStart, document.getTextLength()),
                    new NotificationHyperlinkInfo(notification, href));
          content = content.substring(linkEnd + A_CLOSING.length());
          continue;
        }
      }

      if (isTag(HTML_TAGS, tagStart)) {
        hasHtml = true;
        if (NEW_LINES.contains(tagStart)) {
          if (initialLen != document.getTextLength()) {
            lineSeparators.add(document.createRangeMarker(TextRange.from(document.getTextLength(), 0)));
          }
        }
        else if (!isTag(SKIP_TAGS, tagStart)) {
          showMore.set(true);
        }
      }
      else {
        appendText(document, content.substring(tagMatcher.start(), tagMatcher.end()));
      }
      content = content.substring(tagMatcher.end());
    }
    lineSeparators.removeIf(next -> next.getEndOffset() == document.getTextLength());
    return hasHtml;
  }

  @NonNls private static final String[] HTML_TAGS =
    {"a", "abbr", "acronym", "address", "applet", "area", "article", "aside", "audio", "b", "base", "basefont", "bdi", "bdo", "big",
      "blockquote", "body", "br", "button", "canvas", "caption", "center", "cite", "code", "col", "colgroup", "command", "datalist", "dd",
      "del", "details", "dfn", "dir", "div", "dl", "dt", "em", "embed", "fieldset", "figcaption", "figure", "font", "footer", "form",
      "frame", "frameset", "h1", "h2", "h3", "h4", "h5", "h6", "head", "header", "hgroup", "hr", "html", "i", "iframe", "img", "input",
      "ins", "kbd", "keygen", "label", "legend", "li", "link", "map", "mark", "menu", "meta", "meter", "nav", "nobr", "noframes", "noscript",
      "object", "ol", "optgroup", "option", "output", "p", "param", "pre", "progress", "q", "rp", "rt", "ruby", "s", "samp", "script",
      "section", "select", "small", "source", "span", "strike", "strong", "style", "sub", "summary", "sup", "table", "tbody", "td",
      "textarea", "tfoot", "th", "thead", "time", "title", "tr", "track", "tt", "u", "ul", "var", "video", "wbr"};

  @NonNls private static final String[] SKIP_TAGS = {"html", "body", "b", "i", "font", "ul"};

  private static boolean isTag(String @NotNull [] tags, @NotNull String tag) {
    tag = tag.substring(1, tag.length() - 1); // skip <>
    tag = StringUtil.trimEnd(StringUtil.trimStart(tag, "/"), "/"); // skip /
    int index = tag.indexOf(' ');
    if (index != -1) {
      tag = tag.substring(0, index);
    }
    return ArrayUtil.indexOf(tags, tag) != -1;
  }

  private static void insertNewLineSubstitutors(Document document, AtomicBoolean showMore, List<? extends RangeMarker> lineSeparators) {
    for (RangeMarker marker : lineSeparators) {
      if (!marker.isValid()) {
        showMore.set(true);
        continue;
      }

      int offset = marker.getStartOffset();
      if (offset == 0 || offset == document.getTextLength()) {
        continue;
      }
      boolean spaceBefore = offset > 0 && Character.isWhitespace(document.getCharsSequence().charAt(offset - 1));
      if (offset < document.getTextLength()) {
        boolean spaceAfter = Character.isWhitespace(document.getCharsSequence().charAt(offset));
        int next = CharArrayUtil.shiftForward(document.getCharsSequence(), offset, " \t");
        if (next < document.getTextLength() && !Character.isLowerCase(document.getCharsSequence().charAt(next))) {
          document.insertString(offset, (spaceBefore ? "" : " ") + "//" + (spaceAfter ? "" : " "));
          continue;
        }
        if (spaceAfter) {
          continue;
        }
      }
      if (spaceBefore) {
        continue;
      }

      document.insertString(offset, " ");
    }
  }

  private static void removeJavaNewLines(Document document, List<? super RangeMarker> lineSeparators, String indent, boolean hasHtml) {
    CharSequence text = document.getCharsSequence();
    int i = 0;
    while (true) {
      i = StringUtil.indexOf(text, '\n', i);
      if (i < 0) break;
      int j = i + 1;
      if (StringUtil.startsWith(text, j, indent)) {
        j += indent.length();
      }
      document.deleteString(i, j);
      if (!hasHtml) {
        lineSeparators.add(document.createRangeMarker(TextRange.from(i, 0)));
      }
    }
  }

  private static void appendText(Document document, String text) {
    text = StringUtil.replace(text, "&nbsp;", " ");
    text = StringUtil.replace(text, "&raquo;", ">>");
    text = StringUtil.replace(text, "&laquo;", "<<");
    text = StringUtil.replace(text, "&hellip;", "...");
    document.insertString(document.getTextLength(), StringUtil.unescapeXmlEntities(text));
  }

  public static class LogEntry {
    public final String message;
    public final @NlsContexts.StatusBarText String status;
    public final List<Pair<TextRange, HyperlinkInfo>> links;
    public final int titleLength;

    public LogEntry(@NotNull String message, @NotNull @Nls String status, @NotNull List<Pair<TextRange, HyperlinkInfo>> links, int titleLength) {
      this.message = message;
      this.status = status;
      this.links = links;
      this.titleLength = titleLength;
    }
  }

  public static @Nullable ToolWindow getEventLog(@Nullable Project project) {
    return project == null ? null : ToolWindowManager.getInstance(project).getToolWindow(LOG_TOOL_WINDOW_ID);
  }

  public static void toggleLog(final @Nullable Project project, final @Nullable Notification notification) {
    if (ActionCenter.isEnabled()) {
      if (project != null) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(NotificationsToolWindowFactory.ID);
        if (toolWindow != null) {
          if (toolWindow.isVisible()) {
            toolWindow.hide();
          }
          else {
            toolWindow.activate(null);
          }
        }
      }
      return;
    }
    final ToolWindow eventLog = getEventLog(project);
    if (eventLog != null) {
      if (!eventLog.isVisible()) {
        activate(eventLog, notification == null ? null :notification.getGroupId(), null);
      }
      else {
        eventLog.hide(null);
      }
    }
  }

  private static void activate(@NotNull ToolWindow eventLog, @Nullable String groupId, @Nullable Runnable runnable) {
    eventLog.activate(() -> {
      if (groupId == null) return;
      String contentName = getContentName(groupId);
      Content content = eventLog.getContentManager().findContent(contentName);
      if (content != null) {
        eventLog.getContentManager().setSelectedContent(content);
      }
      if (runnable != null) {
        runnable.run();
      }
    }, true);
  }

  static final class ProjectTracker implements Disposable {
    private final Map<String, EventLogConsole> myCategoryMap = new ConcurrentHashMap<>();
    private final List<Notification> myInitial = ContainerUtil.createLockFreeCopyOnWriteList();
    private final LogModel myProjectModel;
    private final @NotNull Project myProject;

    ProjectTracker(@NotNull Project project) {
      myProjectModel = new LogModel(project);
      myProject = project;

      EventLog appService = ApplicationManager.getApplication().getServiceIfCreated(EventLog.class);
      if (appService != null) {
        for (Notification notification : appService.myModel.takeNotifications()) {
          printNotification(notification);
        }
      }

      project.getMessageBus().simpleConnect().subscribe(Notifications.TOPIC, new Notifications() {
        @Override
        public void notify(@NotNull Notification notification) {
          printNotification(notification);
        }
      });
    }

    @Override
    public void dispose() {
      EventLog appService = ApplicationManager.getApplication().getServiceIfCreated(EventLog.class);
      if (appService != null) {
        appService.myModel.setStatusMessage(null, 0);
      }
      StatusBar.Info.set("", null, LOG_REQUESTOR);
      myProjectModel.projectDispose(appService == null ? null : appService.myModel);
    }

    void initDefaultContent() {
      createNewContent(DEFAULT_CATEGORY);

      if (myInitial.isEmpty()) {
        return;
      }

      List<Notification> notifications = new ArrayList<>(myInitial);
      myInitial.clear();
      StartupManager.getInstance(myProject).runAfterOpened(() -> {
        for (Notification notification : notifications) {
          if (ShutDownTracker.isShutdownHookRunning()) {
            return;
          }

          EventLogConsole console = Objects.requireNonNull(getConsole(notification));
          ModalityUiUtil.invokeLaterIfNeeded(ModalityState.NON_MODAL, myProject.getDisposed(),
                                             () -> console.doPrintNotification(notification));
        }
      });
    }

    private void printNotification(Notification notification) {
      if (ActionCenter.isEnabled()) {
        return;
      }
      if (!NotificationsConfigurationImpl.getSettings(notification.getGroupId()).isShouldLog()) {
        return;
      }

      myProjectModel.addNotification(notification);

      NotificationCollector.getInstance().logNotificationLoggedInEventLog(myProject, notification);
      EventLogConsole console = getConsole(notification);
      if (console == null) {
        if (!ActionCenter.isEnabled()) {
          myInitial.add(notification);
        }
      }
      else {
        StartupManager.getInstance(myProject).runAfterOpened(() -> {
          if (!ShutDownTracker.isShutdownHookRunning()) {
            ModalityUiUtil.invokeLaterIfNeeded(ModalityState.NON_MODAL, myProject.getDisposed(),
                                               () -> console.doPrintNotification(notification));
          }
        });
      }
    }

    private void showNotification(final @NotNull String groupId, final @NotNull List<String> ids) {
      ToolWindow eventLog = getEventLog(myProject);
      if (eventLog != null) {
        activate(eventLog, groupId, () -> {
          EventLogConsole console = getConsole(groupId);
          if (console != null) {
            console.showNotification(ids);
          }
        });
      }
    }

    private void clearNMore(@NotNull Collection<String> groups) {
      for (String group : groups) {
        EventLogConsole console = myCategoryMap.get(getContentName(group));
        if (console != null) {
          console.clearNMore();
        }
      }
    }

    private boolean isClearAvailable() {
      if (!myProjectModel.getNotifications().isEmpty()) {
        return true;
      }
      for (EventLogConsole console : myCategoryMap.values()) {
        if (console.getConsoleEditor().getDocument().getTextLength() > 0) {
          return true;
        }
      }
      return false;
    }

    private void doClear() {
      for (Notification notification : myProjectModel.getNotifications()) {
        notification.expire();
        myProjectModel.removeNotification(notification);
      }
      myInitial.clear();
      myProjectModel.setStatusMessage(null, 0);

      for (EventLogConsole console : myCategoryMap.values()) {
        Document document = console.getConsoleEditor().getDocument();
        document.deleteString(0, document.getTextLength());
      }
    }

    private @Nullable EventLogConsole getConsole(@NotNull Notification notification) {
      return getConsole(notification.getGroupId());
    }

    private @Nullable EventLogConsole getConsole(@NotNull String groupId) {
      if (ActionCenter.isEnabled()) {
        return null;
      }
      if (myCategoryMap.get(DEFAULT_CATEGORY) == null) {
        // still not initialized
        return null;
      }

      String name = getContentName(groupId);
      EventLogConsole console = myCategoryMap.get(name);
      return console == null ? createNewContent(name) : console;
    }

    private @NotNull EventLogConsole createNewContent(@NotNull @Nls String name) {
      ApplicationManager.getApplication().assertIsDispatchThread();
      ToolWindow toolWindow = Objects.requireNonNull(ToolWindowManager.getInstance(myProject).getToolWindow(LOG_TOOL_WINDOW_ID));
      EventLogConsole newConsole = new EventLogConsole(myProjectModel, toolWindow.getDisposable());
      EventLogToolWindowFactory.createContent(myProject, toolWindow, newConsole, name);
      myCategoryMap.put(name, newConsole);
      return newConsole;
    }
  }

  private static @NotNull @Nls String getContentName(@NotNull String groupId) {
    for (EventLogCategory category : EventLogCategory.EP_NAME.getExtensionList()) {
      if (category.acceptsNotification(groupId)) {
        return category.getDisplayName();
      }
    }
    return DEFAULT_CATEGORY;
  }

  static @NotNull ProjectTracker getProjectService(@NotNull Project project) {
    return project.getService(ProjectTracker.class);
  }

  private static final class NotificationHyperlinkInfo implements HyperlinkInfo {
    private final Notification myNotification;
    private final String myHref;

    NotificationHyperlinkInfo(Notification notification, String href) {
      myNotification = notification;
      myHref = href;
    }

    @Override
    public void navigate(@NotNull Project project) {
      NotificationListener listener = myNotification.getListener();
      if (listener != null) {
        EventLogConsole console = Objects.requireNonNull(getProjectService(project).getConsole(myNotification));
        JComponent component = console.getConsoleEditor().getContentComponent();
        listener.hyperlinkUpdate(myNotification, IJSwingUtilities.createHyperlinkEvent(myHref, component));
      }
    }
  }

  static final class ShowBalloon implements HyperlinkInfo {
    private final Notification myNotification;
    private RangeHighlighter myRangeHighlighter;

    ShowBalloon(Notification notification) {
      myNotification = notification;
    }

    public void setRangeHighlighter(RangeHighlighter rangeHighlighter) {
      myRangeHighlighter = rangeHighlighter;
    }

    @Override
    public void navigate(@NotNull Project project) {
      hideBalloon(myNotification);

      for (Notification notification : getLogModel(project).getNotifications()) {
        hideBalloon(notification);
      }

      EventLogConsole console = Objects.requireNonNull(getProjectService(project).getConsole(myNotification));
      if (myRangeHighlighter == null || !myRangeHighlighter.isValid()) {
        return;
      }
      RelativePoint target = console.getRangeHighlighterLocation(myRangeHighlighter);
      if (target != null) {
        IdeFrame frame = WindowManager.getInstance().getIdeFrame(project);
        assert frame != null;
        Balloon balloon =
          NotificationsManagerImpl.createBalloon(frame, myNotification, true, true, BalloonLayoutData.fullContent(), project);
        balloon.show(target, Balloon.Position.above);
        NotificationCollector.getInstance().logBalloonShownFromEventLog(project, myNotification);
      }
    }

    private static void hideBalloon(Notification notification) {
      Balloon balloon = notification.getBalloon();
      if (balloon != null) {
        balloon.hide(true);
      }
    }
  }

  static final class MyNotificationListener implements Notifications {
    @Override
    public void notify(@NotNull Notification notification) {
      if (ActionCenter.isEnabled()) {
        return;
      }
      ProjectManager projectManager = ProjectManager.getInstanceIfCreated();
      Project[] openProjects = projectManager == null ? null : projectManager.getOpenProjects();
      if (openProjects == null || openProjects.length == 0) {
        getApplicationService().myModel.addNotification(notification);
      }
      else {
        for (Project p : openProjects) {
          if (!p.isDisposed()) {
            getProjectService(p).printNotification(notification);
          }
        }
      }
    }
  }
}
