// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.AntialiasingType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.options.newEditor.SettingsDialog;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.TextFieldWithHistory;
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton;
import com.intellij.ui.components.ComponentsKt;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Consumer;
import com.intellij.util.NotNullProducer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ComparatorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;

public final class SwingHelper {

  private static final Logger LOG = Logger.getInstance(SwingHelper.class);
  private static final String DIALOG_RESIZED_TO_FIT_TEXT = "INTELLIJ_DIALOG_RESIZED_TO_FIT_TEXT";

  /**
   * Creates panel whose content consists of given {@code children} components
   * stacked vertically each on another in a given order.
   *
   * @param childAlignmentX Component.LEFT_ALIGNMENT, Component.CENTER_ALIGNMENT or Component.RIGHT_ALIGNMENT
   * @param children        children components
   * @return created panel
   */
  @NotNull
  public static JPanel newVerticalPanel(float childAlignmentX, Component... children) {
    return newGenericBoxPanel(true, childAlignmentX, children);
  }

  @NotNull
  public static JPanel newLeftAlignedVerticalPanel(Component... children) {
    return newVerticalPanel(Component.LEFT_ALIGNMENT, children);
  }

  @NotNull
  public static JPanel newLeftAlignedVerticalPanel(@NotNull Collection<Component> children) {
    return newVerticalPanel(Component.LEFT_ALIGNMENT, children);
  }

  @NotNull
  public static JPanel newVerticalPanel(float childAlignmentX, @NotNull Collection<Component> children) {
    return newVerticalPanel(childAlignmentX, children.toArray(new Component[0]));
  }

  /**
   * Creates panel whose content consists of given {@code children} components horizontally
   * stacked each on another in a given order.
   *
   * @param childAlignmentY Component.TOP_ALIGNMENT, Component.CENTER_ALIGNMENT or Component.BOTTOM_ALIGNMENT
   * @param children        children components
   * @return created panel
   */
  @NotNull
  public static JPanel newHorizontalPanel(float childAlignmentY, Component... children) {
    return newGenericBoxPanel(false, childAlignmentY, children);
  }

  private static JPanel newGenericBoxPanel(boolean verticalOrientation,
                                           float childAlignment,
                                           Component... children) {
    JPanel panel = new JPanel();
    int axis = verticalOrientation ? BoxLayout.Y_AXIS : BoxLayout.X_AXIS;
    panel.setLayout(new BoxLayout(panel, axis));
    for (Component child : children) {
      panel.add(child, childAlignment);
      if (child instanceof JComponent jChild) {
        if (verticalOrientation) {
          jChild.setAlignmentX(childAlignment);
        }
        else {
          jChild.setAlignmentY(childAlignment);
        }
      }
    }
    return panel;
  }

  @NotNull
  public static JPanel wrapWithoutStretch(@NotNull JComponent component) {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    panel.add(component);
    return panel;
  }

  @NotNull
  public static JPanel wrapWithHorizontalStretch(@NotNull JComponent component) {
    JPanel panel = new JPanel(new BorderLayout(0, 0));
    panel.add(component, BorderLayout.NORTH);
    return panel;
  }

  public static void setPreferredWidthToFitText(@NotNull TextFieldWithHistoryWithBrowseButton component) {
    int childWidth = calcWidthToFitText(component.getChildComponent().getTextEditor(), JBUIScale.scale(32));
    setPreferredWidthForComponentWithBrowseButton(component, childWidth);
  }

  public static void setPreferredWidthToFitText(@NotNull TextFieldWithBrowseButton component) {
    int childWidth = calcWidthToFitText(component.getChildComponent(), JBUIScale.scale(20));
    setPreferredWidthForComponentWithBrowseButton(component, childWidth);
  }

  private static <T extends JComponent> void setPreferredWidthForComponentWithBrowseButton(@NotNull ComponentWithBrowseButton<T> component,
                                                                                           int childPrefWidth) {
    Dimension buttonPrefSize = component.getButton().getPreferredSize();
    setPreferredWidth(component, childPrefWidth + buttonPrefSize.width);
  }

  public static void setPreferredWidthToFitText(@NotNull JTextField textField) {
    setPreferredWidthToFitText(textField, JBUIScale.scale(15));
  }

  public static void setPreferredWidthToFitText(@NotNull JTextField textField, int additionalWidth) {
    setPreferredSizeToFitText(textField, StringUtil.notNullize(textField.getText()), additionalWidth);
  }

  public static void setPreferredWidthToFitText(@NotNull JTextField textField, @NotNull String text) {
    setPreferredSizeToFitText(textField, text, JBUIScale.scale(15));
  }

  private static void setPreferredSizeToFitText(@NotNull JTextField textField, @NotNull String text, int additionalWidth) {
    int width = calcWidthToFitText(textField, text, additionalWidth);
    setPreferredWidth(textField, width);
  }

  private static int calcWidthToFitText(@NotNull JTextField textField, int additionalWidth) {
    return calcWidthToFitText(textField, textField.getText(), additionalWidth);
  }

  private static int calcWidthToFitText(@NotNull JTextField textField, @NotNull String text, int additionalWidth) {
    return textField.getFontMetrics(textField.getFont()).stringWidth(text) + additionalWidth;
  }

  public static void adjustDialogSizeToFitPreferredSize(@NotNull DialogWrapper dialogWrapper) {
    JRootPane rootPane = dialogWrapper.getRootPane();
    if (rootPane == null) return;
    Dimension componentSize = rootPane.getSize();
    Dimension componentPreferredSize = rootPane.getPreferredSize();
    if (componentPreferredSize.width <= componentSize.width && componentPreferredSize.height <= componentSize.height) {
      return;
    }
    int dw = Math.max(0, componentPreferredSize.width - componentSize.width);
    int dh = Math.max(0, componentPreferredSize.height - componentSize.height);

    Dimension oldDialogSize = dialogWrapper.getSize();
    Dimension newDialogSize = new Dimension(oldDialogSize.width + dw, oldDialogSize.height + dh);

    dialogWrapper.setSize(newDialogSize.width, newDialogSize.height);
    rootPane.revalidate();
    rootPane.repaint();

    LOG.debug("DialogWrapper '" + dialogWrapper.getTitle() + "' has been re-sized (added width: " + dw + ", added height: " + dh + ")");
  }

  public static void resizeDialogToFitTextFor(final JComponent @NotNull ... components) {
    if (components.length == 0) return;
    doWithDialogWrapper(components[0], dialogWrapper -> {
      if (dialogWrapper instanceof SettingsDialog || dialogWrapper instanceof SingleConfigurableEditor) {
        for (Component component : components) {
          if (component instanceof TextFieldWithHistoryWithBrowseButton) {
            setPreferredWidthToFitText((TextFieldWithHistoryWithBrowseButton)component);
          }
          else if (component instanceof TextFieldWithBrowseButton) {
            setPreferredWidthToFitText((TextFieldWithBrowseButton)component);
          }
          else if (component instanceof JTextField) {
            setPreferredWidthToFitText((JTextField)component);
          }
        }
        ApplicationManager.getApplication().invokeLater(() -> adjustDialogSizeToFitPreferredSize(dialogWrapper), ModalityState.any());
      }
    });
  }

  private static void doWithDialogWrapper(@NotNull final JComponent component, @NotNull final Consumer<? super DialogWrapper> consumer) {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (component.getClientProperty(DIALOG_RESIZED_TO_FIT_TEXT) != null) {
        return;
      }
      component.putClientProperty(DIALOG_RESIZED_TO_FIT_TEXT, true);
      DialogWrapper dialogWrapper = DialogWrapper.findInstance(component);
      if (dialogWrapper != null) {
        consumer.consume(dialogWrapper);
      }
      else {
        UiNotifyConnector.doWhenFirstShown(component, () -> {
          DialogWrapper dialogWrapper1 = DialogWrapper.findInstance(component);
          if (dialogWrapper1 != null) {
            consumer.consume(dialogWrapper1);
          }
        });
      }
    });
  }

  public static <T> void updateItems(@NotNull JComboBox<T> comboBox,
                                     @NotNull List<? extends T> newItems,
                                     @Nullable T newSelectedItemIfSelectionCannotBePreserved) {
    if (!shouldUpdate(comboBox, newItems)) {
      return;
    }
    Object itemToSelect = comboBox.getSelectedItem();
    boolean preserveSelection = true;
    //noinspection SuspiciousMethodCalls
    if (itemToSelect == null || !newItems.contains(itemToSelect)) {
      if (newSelectedItemIfSelectionCannotBePreserved != null && newItems.contains(newSelectedItemIfSelectionCannotBePreserved)) {
        itemToSelect = newSelectedItemIfSelectionCannotBePreserved;
      }
      else {
        itemToSelect = null;
        preserveSelection = false;
      }
    }
    comboBox.removeAllItems();
    for (T newItem : newItems) {
      comboBox.addItem(newItem);
    }
    if (preserveSelection) {
      int count = comboBox.getItemCount();
      for (int i = 0; i < count; i++) {
        Object item = comboBox.getItemAt(i);
        if (ComparatorUtil.equalsNullable(itemToSelect, item)) {
          comboBox.setSelectedIndex(i);
          break;
        }
      }
    }
  }

  private static <T> boolean shouldUpdate(@NotNull JComboBox<T> comboBox, @NotNull List<? extends T> newItems) {
    int count = comboBox.getItemCount();
    if (newItems.size() != count) {
      return true;
    }
    for (int i = 0; i < count; i++) {
      Object oldItem = comboBox.getItemAt(i);
      T newItem = newItems.get(i);
      if (!ComparatorUtil.equalsNullable(oldItem, newItem)) {
        return true;
      }
    }
    return false;
  }

  public static void setNoBorderCellRendererFor(@NotNull TableColumn column) {
    final TableCellRenderer previous = column.getCellRenderer();
    column.setCellRenderer(new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     boolean hasFocus,
                                                     int row,
                                                     int column) {
        Component component;
        if (previous != null) {
          component = previous.getTableCellRendererComponent(table, value, isSelected, false, row, column);
        }
        else {
          component = super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
        }
        if (component instanceof JComponent) {
          ((JComponent)component).setBorder(null);
        }
        return component;
      }
    });
  }

  public static void addHistoryOnExpansion(@NotNull final TextFieldWithHistory textFieldWithHistory,
                                           @NotNull final NotNullProducer<? extends List<String>> historyProvider) {
    textFieldWithHistory.addPopupMenuListener(new PopupMenuListener() {
      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        List<String> history = historyProvider.produce();
        setHistory(textFieldWithHistory, ContainerUtil.notNullize(history), true);
        // one-time initialization
        textFieldWithHistory.removePopupMenuListener(this);
      }

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
      }

      @Override
      public void popupMenuCanceled(PopupMenuEvent e) {
      }
    });
  }

  public static void setHistory(@NotNull TextFieldWithHistory textFieldWithHistory,
                                @NotNull List<String> history,
                                boolean mergeWithPrevHistory) {
    Set<String> newHistorySet = new HashSet<>(history);
    List<String> prevHistory = textFieldWithHistory.getHistory();
    List<String> mergedHistory = new ArrayList<>(history.size());
    if (mergeWithPrevHistory) {
      for (String item : prevHistory) {
        if (!newHistorySet.contains(item)) {
          mergedHistory.add(item);
        }
      }
    }
    mergedHistory.addAll(history);
    String oldText = StringUtil.notNullize(textFieldWithHistory.getText());
    String oldSelectedItem = ObjectUtils.tryCast(textFieldWithHistory.getSelectedItem(), String.class);
    if (!mergedHistory.contains(oldSelectedItem)) {
      oldSelectedItem = null;
    }
    textFieldWithHistory.setHistory(mergedHistory);
    setLongestAsPrototype(textFieldWithHistory, mergedHistory);
    if (oldSelectedItem != null) {
      textFieldWithHistory.setSelectedItem(oldSelectedItem);
    }
    if (!oldText.equals(oldSelectedItem)) {
      textFieldWithHistory.setText(oldText);
    }
  }

  private static void setLongestAsPrototype(@NotNull JComboBox comboBox, @NotNull List<String> variants) {
    Object prototypeDisplayValue = comboBox.getPrototypeDisplayValue();
    String prototypeDisplayValueStr = null;
    if (prototypeDisplayValue instanceof String) {
      prototypeDisplayValueStr = (String)prototypeDisplayValue;
    }
    else if (prototypeDisplayValue != null) {
      return;
    }
    String longest = StringUtil.notNullize(prototypeDisplayValueStr);
    boolean updated = false;
    for (@NlsSafe String s : variants) {
      if (longest.length() < s.length()) {
        longest = s;
        updated = true;
      }
    }
    if (updated) {
      comboBox.setPrototypeDisplayValue(longest);
    }
  }

  public static void installFileCompletionAndBrowseDialog(@Nullable Project project,
                                                          @NotNull TextFieldWithHistoryWithBrowseButton textFieldWithHistoryWithBrowseButton,
                                                          @NotNull @Nls(capitalization = Nls.Capitalization.Title) String browseDialogTitle,
                                                          @NotNull FileChooserDescriptor fileChooserDescriptor) {
    doInstall(project,
              textFieldWithHistoryWithBrowseButton,
              textFieldWithHistoryWithBrowseButton.getChildComponent().getTextEditor(),
              browseDialogTitle,
              fileChooserDescriptor,
              TextComponentAccessors.TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT);
  }

  public static void installFileCompletionAndBrowseDialog(@Nullable Project project,
                                                          @NotNull TextFieldWithBrowseButton textFieldWithBrowseButton,
                                                          @NotNull @Nls(capitalization = Nls.Capitalization.Title) String browseDialogTitle,
                                                          @NotNull FileChooserDescriptor fileChooserDescriptor) {
    doInstall(project,
              textFieldWithBrowseButton,
              textFieldWithBrowseButton.getTextField(),
              browseDialogTitle,
              fileChooserDescriptor,
              TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
  }

  private static <T extends JComponent> void doInstall(@Nullable Project project,
                                                       @NotNull ComponentWithBrowseButton<T> componentWithBrowseButton,
                                                       @NotNull JTextField textField,
                                                       @NotNull @Nls(capitalization = Nls.Capitalization.Title) String browseDialogTitle,
                                                       @NotNull FileChooserDescriptor fileChooserDescriptor,
                                                       @NotNull TextComponentAccessor<T> textComponentAccessor) {
    ComponentsKt.installFileCompletionAndBrowseDialog(
      project,
      componentWithBrowseButton,
      textField,
      browseDialogTitle,
      fileChooserDescriptor.withShowHiddenFiles(SystemInfo.isUnix),
      textComponentAccessor
    );
  }

  /**
   * @deprecated use {@link com.intellij.ui.components.BrowserLink} instead
   */
  @Deprecated(forRemoval = true)
  @NotNull
  public static HyperlinkLabel createWebHyperlink(@NlsSafe @NotNull String url) {
    return createWebHyperlink(url, url);
  }

  /**
   * @deprecated use {@link com.intellij.ui.components.BrowserLink} instead
   */
  @Deprecated(forRemoval = true)
  @NotNull
  public static HyperlinkLabel createWebHyperlink(@NlsContexts.LinkLabel @NotNull String text, @NotNull String url) {
    HyperlinkLabel hyperlink = new HyperlinkLabel(text);
    hyperlink.setHyperlinkTarget(url);

    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new OpenLinkInBrowser(url));
    actionGroup.add(new CopyLinkAction(url));

    hyperlink.setComponentPopupMenu(ActionManager.getInstance().createActionPopupMenu("web hyperlink", actionGroup).getComponent());
    return hyperlink;
  }

  public static void setPreferredWidth(@NotNull Component component, int width) {
    Dimension preferredSize = component.getPreferredSize();
    preferredSize.width = width;
    component.setPreferredSize(preferredSize);
  }

  public static boolean scrollToReference(JEditorPane view, String reference) {
    reference = StringUtil.trimStart(reference, "#");
    List<String> toCheck = Arrays.asList("a", "h1", "h2", "h3", "h4");
    Document document = view.getDocument();
    if (document instanceof HTMLDocument) {
      List<Element> list = new ArrayList<>();
      for (Element root : document.getRootElements()) {
        getAllElements(root, list, toCheck);
      }
      for (Element element : list) {
          AttributeSet attributes = element.getAttributes();
          String nm = (String)attributes.getAttribute(HTML.Attribute.NAME);
          if (nm == null) nm = (String)attributes.getAttribute(HTML.Attribute.ID);
          if ((nm != null) && nm.equals(reference)) {
            try {
              int pos = element.getStartOffset();
              Rectangle r = view.modelToView(pos);
              if (r != null) {
                Rectangle vis = view.getVisibleRect();
                r.y -= 5;
                r.height = vis.height;
                view.scrollRectToVisible(r);
                return true;
              }
            }
            catch (BadLocationException ex) {
              //ignore
            }
          }
      }
    }
    return false;
  }

  private static void getAllElements(Element root, List<? super Element> list, List<String> toCheck) {
    if (toCheck.contains(StringUtil.toLowerCase(root.getName()))) {
      list.add(root);
    }
    for (int i = 0; i < root.getElementCount(); i++) {
      getAllElements(root.getElement(i), list, toCheck);
    }
  }

  public static class HtmlViewerBuilder {
    private boolean myCarryTextOver;
    private String myDisabledHtml;
    private Font myFont;
    private Color myBackground;
    private Color myForeground;

    public JEditorPane create() {
      final JEditorPane textPane = new JEditorPane() {
        private boolean myEnabled = true;
        private String myEnabledHtml;

        @Override
        public Dimension getPreferredSize() {
          // This trick makes text component to carry text over to the next line
          // if the text line width exceeds parent's width
          Dimension dimension = super.getPreferredSize();
          if (myCarryTextOver) {
            dimension.width = 0;
          }
          return dimension;
        }

        @Override
        public void setText(@NlsSafe String t) {
          if (myDisabledHtml != null) {
            if (myEnabled) {
              myEnabledHtml = t;
            }
          }
          super.setText(t);
        }

        @Override
        public void setEnabled(boolean enabled) {
          if (myDisabledHtml != null) {
            myEnabled = enabled;
            if (myEnabled) {
              setText(myEnabledHtml);
            } else {
              setText(myDisabledHtml);
            }
            super.setEnabled(true);
          } else {
            super.setEnabled(enabled);
          }
        }
      };
      textPane.setFont(myFont != null ? myFont : StartupUiUtil.getLabelFont());
      textPane.setEditorKit(HTMLEditorKitBuilder.simple());
      textPane.setEditable(false);
      if (myBackground != null) {
        textPane.setBackground(myBackground);
      }
      else {
        textPane.setOpaque(false);
      }
      textPane.setForeground(myForeground != null ? myForeground : UIUtil.getLabelForeground());
      textPane.setFocusable(false);
      return textPane;
    }

    public HtmlViewerBuilder setCarryTextOver(boolean carryTextOver) {
      myCarryTextOver = carryTextOver;
      return this;
    }

    public HtmlViewerBuilder setDisabledHtml(String disabledHtml) {
      myDisabledHtml = disabledHtml;
      return this;
    }

    public HtmlViewerBuilder setFont(Font font) {
      myFont = font;
      return this;
    }

    public HtmlViewerBuilder setBackground(Color background) {
      myBackground = background;
      return this;
    }

    public HtmlViewerBuilder setForeground(Color foreground) {
      myForeground = foreground;
      return this;
    }
  }

  @NotNull
  public static JEditorPane createHtmlViewer(boolean lineWrap,
                                             @Nullable Font font,
                                             @Nullable Color background,
                                             @Nullable Color foreground) {
    final JEditorPane textPane;
    if (lineWrap) {
      textPane = new JEditorPane() {
        @Override
        public Dimension getPreferredSize() {
          // This trick makes text component to carry text over to the next line
          // if the text line width exceeds parent's width
          Dimension dimension = super.getPreferredSize();
          dimension.width = 0;
          return dimension;
        }
      };
    }
    else {
      textPane = new JEditorPane();
    }
    GraphicsUtil.setAntialiasingType(textPane, AntialiasingType.getAAHintForSwingComponent());
    textPane.setFont(font != null ? font : StartupUiUtil.getLabelFont());
    textPane.setEditorKit(HTMLEditorKitBuilder.simple());
    textPane.setEditable(false);
    if (background != null) {
      textPane.setBackground(background);
    }
    else {
      NonOpaquePanel.setTransparent(textPane);
    }
    textPane.setForeground(foreground != null ? foreground : UIUtil.getLabelForeground());
    textPane.setFocusable(false);
    return textPane;
  }

  public static void setHtml(@NotNull JEditorPane editorPane,
                             @NotNull @Nls String bodyInnerHtml,
                             @Nullable Color foregroundColor) {
    editorPane.setText(buildHtml(
      UIUtil.getCssFontDeclaration(editorPane.getFont(), foregroundColor, JBUI.CurrentTheme.Link.Foreground.ENABLED, null),
      bodyInnerHtml
    ));
  }

  @NotNull
  public static @Nls String buildHtml(@NotNull @Nls String headInnerHtml, @NotNull @Nls String bodyInnerHtml) {
    if (bodyInnerHtml.contains("<html>")) {
      bodyInnerHtml = UIUtil.getHtmlBody(bodyInnerHtml);
    }
    return HtmlChunk.html().children(
      HtmlChunk.head().addRaw(headInnerHtml),
      HtmlChunk.body().addRaw(bodyInnerHtml)
    ).toString();
  }

  @NotNull
  public static TextFieldWithHistoryWithBrowseButton createTextFieldWithHistoryWithBrowseButton(@Nullable Project project,
                                                                                                @NotNull @NlsContexts.DialogTitle String browseDialogTitle,
                                                                                                @NotNull FileChooserDescriptor fileChooserDescriptor,
                                                                                                @Nullable NotNullProducer<? extends List<String>> historyProvider) {
    return ComponentsKt.textFieldWithHistoryWithBrowseButton(project, browseDialogTitle, fileChooserDescriptor, historyProvider == null ? null : () -> historyProvider.produce());
  }

  @NotNull
  public static <C extends JComponent> ComponentWithBrowseButton<C> wrapWithInfoButton(@NotNull final C component,
                                                                                       @NlsContexts.Tooltip @NotNull String infoButtonTooltip,
                                                                                       @NotNull ActionListener listener) {
    ComponentWithBrowseButton<C> comp = new ComponentWithBrowseButton<>(component, listener);
    FixedSizeButton uiHelpButton = comp.getButton();
    uiHelpButton.setToolTipText(infoButtonTooltip);
    uiHelpButton.setIcon(UIUtil.getBalloonInformationIcon());
    uiHelpButton.setHorizontalAlignment(SwingConstants.CENTER);
    uiHelpButton.setVerticalAlignment(SwingConstants.CENTER);
    return comp;
  }

  private static final class CopyLinkAction extends DumbAwareAction {

    private final String myUrl;

    private CopyLinkAction(@NotNull String url) {
      super(IdeBundle.message("action.text.copy.link.address"), null, PlatformIcons.COPY_ICON);
      myUrl = url;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(true);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Transferable content = new StringSelection(myUrl);
      CopyPasteManager.getInstance().setContents(content);
    }
  }

  private static final class OpenLinkInBrowser extends DumbAwareAction {

    private final String myUrl;

    private OpenLinkInBrowser(@NotNull String url) {
      super(IdeBundle.message("action.text.open.link.in.browser"), null, PlatformIcons.WEB_ICON);
      myUrl = url;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(true);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      BrowserUtil.browse(myUrl);
    }
  }

  public final static String ELLIPSIS = "...";
  public static final String ERROR_STR = "www";
  public static @Nls String truncateStringWithEllipsis(final @Nls String text, final int maxWidth, final FontMetrics fm) {
    return truncateStringWithEllipsis(text, maxWidth, new WidthCalculator() {
      @Override
      public int stringWidth(String s) {
        return fm.stringWidth(s);
      }

      @Override
      public int charWidth(char c) {
        return fm.charWidth(c);
      }
    });
  }

  public interface WidthCalculator {
    int stringWidth(final String s);
    int charWidth(final char c);
  }

  public static @Nls String truncateStringWithEllipsis(@Nls @NotNull final String text, final int maxWidth, final WidthCalculator fm) {
    final int error = fm.stringWidth(ERROR_STR);
    final int wholeWidth = fm.stringWidth(text) + error;
    if (wholeWidth <= maxWidth || text.isEmpty()) return text;
    final int ellipsisWidth = fm.stringWidth(ELLIPSIS) + error; // plus some reserve
    if (ellipsisWidth >= maxWidth) return ELLIPSIS;

    final int availableWidth = maxWidth - ellipsisWidth;
    int currentLen = (int)Math.floor(availableWidth / (((double) wholeWidth) / text.length()));

    final String currentSubstring = text.substring(0, currentLen);
    int realWidth = fm.stringWidth(currentSubstring);

    int delta = 0;
    if (realWidth >= availableWidth) {
      for (int i = currentLen - 1; i >= 0; i--) {
        if ((realWidth - delta) < availableWidth) return text.substring(0, i) + ELLIPSIS;
        delta += fm.charWidth(currentSubstring.charAt(i));
      }
      return text.charAt(0) + ELLIPSIS;
    } else {
      for (int i = currentLen; i < text.length(); i++) {
        if ((realWidth + delta) >= availableWidth) return text.substring(0, i) + ELLIPSIS;
        delta += fm.charWidth(text.charAt(i));
      }
      return text.substring(0, currentLen) + ELLIPSIS;
    }
  }

  public static @NotNull JEditorPane createHtmlLabel(@NotNull @Nls String bodyInnerHtml,
                                                     @Nullable @Nls String disabledBodyInnerHtml,
                                                     @Nullable Consumer<? super String> hyperlinkListener) {
    final Font font = StartupUiUtil.getLabelFont();
    String html = buildHtml(
      UIUtil.getCssFontDeclaration(font, UIUtil.getActiveTextColor(), null, null),
      bodyInnerHtml
    );
    String disabledHtml = buildHtml(
      UIUtil.getCssFontDeclaration(font, NamedColorUtil.getInactiveTextColor(), null, null),
      ObjectUtils.notNull(disabledBodyInnerHtml, bodyInnerHtml)
    );

    final JEditorPane pane = new SwingHelper.HtmlViewerBuilder()
      .setCarryTextOver(false)
      .setFont(StartupUiUtil.getLabelFont())
      .setDisabledHtml(disabledHtml)
      .create();
    pane.setText(html);
    pane.addHyperlinkListener(
      new HyperlinkListener() {
        @Override
        public void hyperlinkUpdate(HyperlinkEvent e) {
          if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            if (hyperlinkListener != null) hyperlinkListener.consume(e.getURL() == null ? "" : e.getURL().toString());
            else BrowserUtil.browse(e.getURL());
          }
        }
      }
    );
    return pane;
  }

  @Nullable
  public static Component getComponentFromRecentMouseEvent() {
    AWTEvent event = IdeEventQueue.getInstance().getTrueCurrentEvent();
    if (event instanceof MouseEvent mouseEvent) {
      Component component = mouseEvent.getComponent();
      if (component != null) {
        component = SwingUtilities.getDeepestComponentAt(component, mouseEvent.getX(), mouseEvent.getY());
        if (component != null) {
          if (component instanceof JTabbedPane tabbedPane) {
            mouseEvent = SwingUtilities.convertMouseEvent(mouseEvent.getComponent(), mouseEvent, component);
            int index = tabbedPane.getUI().tabForCoordinate(tabbedPane, mouseEvent.getX(), mouseEvent.getY());
            if (index != -1) return tabbedPane.getComponentAt(index);
          }
          return component;
        }
      }
    }
    return null;
  }
}
