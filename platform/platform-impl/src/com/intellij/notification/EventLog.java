/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.notification;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.DumbAwareRunnable;
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
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author peter
 */
public class EventLog {
  public static final String LOG_REQUESTOR = "Internal log requestor";
  public static final String LOG_TOOL_WINDOW_ID = "Event Log";
  public static final String HELP_ID = "reference.toolwindows.event.log";
  private static final String A_CLOSING = "</a>";
  private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]*>");
  private static final Pattern A_PATTERN = Pattern.compile("<a ([^>]* )?href=[\"\']([^>]*)[\"\'][^>]*>");
  private static final Set<String> NEW_LINES = ContainerUtil.newHashSet("<br>", "</br>", "<br/>", "<p>", "</p>", "<p/>");
  private static final String DEFAULT_CATEGORY = "";

  private final LogModel myModel = new LogModel(null, ApplicationManager.getApplication());

  public EventLog() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(Notifications.TOPIC, new NotificationsAdapter() {
      @Override
      public void notify(@NotNull Notification notification) {
        final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        if (openProjects.length == 0) {
          myModel.addNotification(notification);
        }
        for (Project p : openProjects) {
          getProjectComponent(p).printNotification(notification);
        }
      }
    });
  }

  public static void expireNotification(@NotNull Notification notification) {
    getApplicationComponent().myModel.removeNotification(notification);
    for (Project p : ProjectManager.getInstance().getOpenProjects()) {
      getProjectComponent(p).myProjectModel.removeNotification(notification);
    }
  }

  public static void showNotification(@NotNull Project project, @NotNull String groupId, @NotNull List<String> ids) {
    getProjectComponent(project).showNotification(groupId, ids);
  }

  private static EventLog getApplicationComponent() {
    return ApplicationManager.getApplication().getComponent(EventLog.class);
  }

  @NotNull
  public static LogModel getLogModel(@Nullable Project project) {
    return project != null ? getProjectComponent(project).myProjectModel : getApplicationComponent().myModel;
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
    getProjectComponent(project).clearNMore(groups);
  }

  @Nullable
  public static Trinity<Notification, String, Long> getStatusMessage(@Nullable Project project) {
    return getLogModel(project).getStatusMessage();
  }

  public static LogEntry formatForLog(@NotNull final Notification notification, final String indent) {
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
    if (NotificationsManagerImpl.newEnabled() && !actions.isEmpty()) {
      String text = "<p>" + StringUtil.join(actions, new Function<AnAction, String>() {
        private int index;

        @Override
        public String fun(AnAction action) {
          return "<a href=\"" + index++ + "\">" + action.getTemplatePresentation().getText() + "</a>";
        }
      }, isLongLine(actions) ? "<br>" : "&nbsp;") + "</p>";
      Notification n = new Notification("", "", ".", NotificationType.INFORMATION, new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification n, @NotNull HyperlinkEvent event) {
          Notification.fire(notification, notification.getActions().get(Integer.parseInt(event.getDescription())));
        }
      });
      if (title.length() > 0 || content.length() > 0) {
        lineSeparators.add(logDoc.createRangeMarker(TextRange.from(logDoc.getTextLength(), 0)));
      }
      hasHtml |= parseHtmlContent(text, n, logDoc, showMore, links, lineSeparators);
    }

    String status = getStatusText(logDoc, showMore, lineSeparators, indent, hasHtml);

    indentNewLines(logDoc, lineSeparators, afterTitle, hasHtml, indent);

    ArrayList<Pair<TextRange, HyperlinkInfo>> list = new ArrayList<>();
    for (RangeMarker marker : links.keySet()) {
      if (!marker.isValid()) {
        showMore.set(true);
        continue;
      }
      list.add(Pair.create(new TextRange(marker.getStartOffset(), marker.getEndOffset()), links.get(marker)));
    }

    if (showMore.get()) {
      String sb = "show balloon";
      if (!logDoc.getText().endsWith(" ")) {
        appendText(logDoc, " ");
      }
      appendText(logDoc, "(" + sb + ")");
      list.add(new Pair<>(TextRange.from(logDoc.getTextLength() - 1 - sb.length(), sb.length()),
                          new ShowBalloon(notification)));
    }

    return new LogEntry(logDoc.getText(), status, list, titleLength);
  }

  @NotNull
  private static String addIndents(@NotNull String text, @NotNull String indent) {
    return StringUtil.replace(text, "\n", "\n" + indent);
  }

  private static boolean isLongLine(@NotNull List<AnAction> actions) {
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

  @NotNull
  private static String truncateLongString(AtomicBoolean showMore, String title) {
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

  private static String getStatusText(DocumentImpl logDoc,
                                      AtomicBoolean showMore,
                                      List<RangeMarker> lineSeparators,
                                      String indent,
                                      boolean hasHtml) {
    DocumentImpl statusDoc = new DocumentImpl(logDoc.getImmutableCharSequence(),true);
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
          links.put(document.createRangeMarker(new TextRange(linkStart, document.getTextLength())),
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
    for (Iterator<RangeMarker> iterator = lineSeparators.iterator(); iterator.hasNext(); ) {
      RangeMarker next = iterator.next();
      if (next.getEndOffset() == document.getTextLength()) {
        iterator.remove();
      }
    }
    return hasHtml;
  }

  private static final String[] HTML_TAGS =
    {"a", "abbr", "acronym", "address", "applet", "area", "article", "aside", "audio", "b", "base", "basefont", "bdi", "bdo", "big",
      "blockquote", "body", "br", "button", "canvas", "caption", "center", "cite", "code", "col", "colgroup", "command", "datalist", "dd",
      "del", "details", "dfn", "dir", "div", "dl", "dt", "em", "embed", "fieldset", "figcaption", "figure", "font", "footer", "form",
      "frame", "frameset", "h1", "h2", "h3", "h4", "h5", "h6", "head", "header", "hgroup", "hr", "html", "i", "iframe", "img", "input",
      "ins", "kbd", "keygen", "label", "legend", "li", "link", "map", "mark", "menu", "meta", "meter", "nav", "noframes", "noscript",
      "object", "ol", "optgroup", "option", "output", "p", "param", "pre", "progress", "q", "rp", "rt", "ruby", "s", "samp", "script",
      "section", "select", "small", "source", "span", "strike", "strong", "style", "sub", "summary", "sup", "table", "tbody", "td",
      "textarea", "tfoot", "th", "thead", "time", "title", "tr", "track", "tt", "u", "ul", "var", "video", "wbr"};

  private static final String[] SKIP_TAGS = {"html", "body", "b", "i", "font"};

  private static boolean isTag(@NotNull String []tags, @NotNull String tag) {
    tag = tag.substring(1, tag.length() - 1); // skip <>
    tag = StringUtil.trimEnd(StringUtil.trimStart(tag, "/"), "/"); // skip /
    int index = tag.indexOf(' ');
    if (index != -1) {
      tag = tag.substring(0, index);
    }
    return ArrayUtil.indexOf(tags, tag) != -1;
  }

  private static void insertNewLineSubstitutors(Document document, AtomicBoolean showMore, List<RangeMarker> lineSeparators) {
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

  private static void removeJavaNewLines(Document document, List<RangeMarker> lineSeparators, String indent, boolean hasHtml) {
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
    document.insertString(document.getTextLength(), StringUtil.unescapeXml(text));
  }

  public static class LogEntry {
    public final String message;
    public final String status;
    public final List<Pair<TextRange, HyperlinkInfo>> links;
    public final int titleLength;

    public LogEntry(@NotNull String message, @NotNull String status, @NotNull List<Pair<TextRange, HyperlinkInfo>> links, int titleLength) {
      this.message = message;
      this.status = status;
      this.links = links;
      this.titleLength = titleLength;
    }
  }

  @Nullable
  public static ToolWindow getEventLog(Project project) {
    return project == null ? null : ToolWindowManager.getInstance(project).getToolWindow(LOG_TOOL_WINDOW_ID);
  }

  public static void toggleLog(@Nullable final Project project, @Nullable final Notification notification) {
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

  private static void activate(@NotNull ToolWindow eventLog, @Nullable final String groupId, @Nullable final Runnable r) {
    eventLog.activate(() -> {
      if (groupId == null) return;
      String contentName = getContentName(groupId);
      Content content = eventLog.getContentManager().findContent(contentName);
      if (content != null) {
        eventLog.getContentManager().setSelectedContent(content);
      }
      if (r != null) {
        r.run();
      }
    }, true);
  }

  public static class ProjectTracker extends AbstractProjectComponent {
    private final Map<String, EventLogConsole> myCategoryMap = ContainerUtil.newConcurrentMap();
    private final List<Notification> myInitial = ContainerUtil.createLockFreeCopyOnWriteList();
    private final LogModel myProjectModel;

    public ProjectTracker(@NotNull final Project project) {
      super(project);

      myProjectModel = new LogModel(project, project);

      for (Notification notification : getApplicationComponent().myModel.takeNotifications()) {
        printNotification(notification);
      }

      project.getMessageBus().connect(project).subscribe(Notifications.TOPIC, new NotificationsAdapter() {
        @Override
        public void notify(@NotNull Notification notification) {
          printNotification(notification);
        }
      });
    }

    void initDefaultContent() {
      createNewContent(DEFAULT_CATEGORY);

      for (Notification notification : myInitial) {
        doPrintNotification(notification, ObjectUtils.assertNotNull(getConsole(notification)));
      }
      myInitial.clear();
    }

    @Override
    public void projectOpened() {
    }

    @Override
    public void projectClosed() {
      getApplicationComponent().myModel.setStatusMessage(null, 0);
      StatusBar.Info.set("", null, LOG_REQUESTOR);
    }

    private void printNotification(Notification notification) {
      if (!NotificationsConfigurationImpl.getSettings(notification.getGroupId()).isShouldLog()) {
        return;
      }
      myProjectModel.addNotification(notification);

      EventLogConsole console = getConsole(notification);
      if (console == null) {
        myInitial.add(notification);
      }
      else {
        doPrintNotification(notification, console);
      }
    }

    private void doPrintNotification(@NotNull final Notification notification, @NotNull final EventLogConsole console) {
      StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new DumbAwareRunnable() {
        @Override
        public void run() {
          if (!ShutDownTracker.isShutdownHookRunning() && !myProject.isDisposed()) {
            ApplicationManager.getApplication().runReadAction(() -> console.doPrintNotification(notification));
          }
        }
      });
    }

    private void showNotification(@NotNull final String groupId, @NotNull final List<String> ids) {
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

    @Nullable
    private EventLogConsole getConsole(@NotNull Notification notification) {
      return getConsole(notification.getGroupId());
    }

    @Nullable
    private EventLogConsole getConsole(@NotNull String groupId) {
      if (myCategoryMap.get(DEFAULT_CATEGORY) == null) return null; // still not initialized

      String name = getContentName(groupId);
      EventLogConsole console = myCategoryMap.get(name);
      return console != null ? console : createNewContent(name);
    }

    @NotNull
    private EventLogConsole createNewContent(String name) {
      ApplicationManager.getApplication().assertIsDispatchThread();
      EventLogConsole newConsole = new EventLogConsole(myProjectModel);
      EventLogToolWindowFactory.createContent(myProject, getEventLog(myProject), newConsole, name);
      myCategoryMap.put(name, newConsole);

      return newConsole;
    }

  }

  @NotNull
  private static String getContentName(String groupId) {
    for (EventLogCategory category : EventLogCategory.EP_NAME.getExtensions()) {
      if (category.acceptsNotification(groupId)) {
        return category.getDisplayName();
      }
    }
    return DEFAULT_CATEGORY;
  }

  static ProjectTracker getProjectComponent(Project project) {
    return project.getComponent(ProjectTracker.class);
  }

  private static class NotificationHyperlinkInfo implements HyperlinkInfo {
    private final Notification myNotification;
    private final String myHref;

    public NotificationHyperlinkInfo(Notification notification, String href) {
      myNotification = notification;
      myHref = href;
    }

    @Override
    public void navigate(Project project) {
      NotificationListener listener = myNotification.getListener();
      if (listener != null) {
        EventLogConsole console = ObjectUtils.assertNotNull(getProjectComponent(project).getConsole(myNotification));
        JComponent component = console.getConsoleEditor().getContentComponent();
        listener.hyperlinkUpdate(myNotification, IJSwingUtilities.createHyperlinkEvent(myHref, component));
      }
    }
  }

  static class ShowBalloon implements HyperlinkInfo {
    private final Notification myNotification;
    private RangeHighlighter myRangeHighlighter;

    public ShowBalloon(Notification notification) {
      myNotification = notification;
    }

    public void setRangeHighlighter(RangeHighlighter rangeHighlighter) {
      myRangeHighlighter = rangeHighlighter;
    }

    @Override
    public void navigate(Project project) {
      hideBalloon(myNotification);

      for (Notification notification : getLogModel(project).getNotifications()) {
        hideBalloon(notification);
      }

      EventLogConsole console = ObjectUtils.assertNotNull(getProjectComponent(project).getConsole(myNotification));
      if (myRangeHighlighter == null || !myRangeHighlighter.isValid()) {
        return;
      }
      RelativePoint target = console.getRangeHighlighterLocation(myRangeHighlighter);
      if (target != null) {
        IdeFrame frame = WindowManager.getInstance().getIdeFrame(project);
        assert frame != null;
        Ref<Object> layoutDataRef = null;
        if (NotificationsManagerImpl.newEnabled()) {
          BalloonLayoutData layoutData = new BalloonLayoutData();
          layoutData.groupId = "";
          layoutData.showFullContent = true;
          layoutData.showSettingButton = false;
          layoutDataRef = new Ref<>(layoutData);
        }
        Balloon balloon = NotificationsManagerImpl.createBalloon(frame, myNotification, true, true, layoutDataRef, project);
        balloon.show(target, Balloon.Position.above);
      }
    }

    private static void hideBalloon(Notification notification) {
      Balloon balloon = notification.getBalloon();
      if (balloon != null) {
        balloon.hide(true);
      }
    }
  }
}
