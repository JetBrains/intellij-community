/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.TextFieldWithHistory;
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton;
import com.intellij.util.NotNullProducer;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ComparatorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class SwingHelper {

  private static final Logger LOG = Logger.getInstance(SwingHelper.class);

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
    return newVerticalPanel(childAlignmentX, children.toArray(new Component[children.size()]));
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

  @NotNull
  public static JPanel newHorizontalPanel(float childAlignmentY, @NotNull Collection<Component> children) {
    return newHorizontalPanel(childAlignmentY, children.toArray(new Component[children.size()]));
  }

  private static JPanel newGenericBoxPanel(boolean verticalOrientation,
                                           float childAlignment,
                                           Component... children) {
    JPanel panel = new JPanel();
    int axis = verticalOrientation ? BoxLayout.Y_AXIS : BoxLayout.X_AXIS;
    panel.setLayout(new BoxLayout(panel, axis));
    for (Component child : children) {
      panel.add(child, childAlignment);
      if (child instanceof JComponent) {
        JComponent jChild = (JComponent)child;
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
    int childWidth = calcWidthToFitText(component.getChildComponent().getTextEditor(), 35);
    setPreferredWidthForComponentWithBrowseButton(component, childWidth);
  }

  public static void setPreferredWidthToFitText(@NotNull TextFieldWithBrowseButton component) {
    int childWidth = calcWidthToFitText(component.getChildComponent(), 20);
    setPreferredWidthForComponentWithBrowseButton(component, childWidth);
  }

  private static <T extends JComponent> void setPreferredWidthForComponentWithBrowseButton(@NotNull ComponentWithBrowseButton<T> component,
                                                                                           int childPrefWidth) {
    Dimension buttonPrefSize = component.getButton().getPreferredSize();
    setPreferredWidth(component, childPrefWidth + buttonPrefSize.width);
  }

  public static void setPreferredWidthToFitText(@NotNull JTextField textField) {
    setPreferredWidthToFitText(textField, 15);
  }

  public static void setPreferredWidthToFitText(@NotNull JTextField textField, int additionalWidth) {
    setPreferredSizeToFitText(textField, StringUtil.notNullize(textField.getText()), additionalWidth);
  }

  public static void setPreferredWidthToFitText(@NotNull JTextField textField, @NotNull String text) {
    setPreferredSizeToFitText(textField, text, 15);
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

    LOG.info("DialogWrapper '" + dialogWrapper.getTitle() + "' has been re-sized (added width: " + dw + ", added height: " + dh + ")");
  }

  public static <T> void updateItems(@NotNull JComboBox comboBox,
                                     @NotNull List<T> newItems,
                                     @Nullable T newSelectedItemIfSelectionCannotBePreserved) {
    if (!shouldUpdate(comboBox, newItems)) {
      return;
    }
    Object selectedItem = comboBox.getSelectedItem();
    //noinspection SuspiciousMethodCalls
    if (selectedItem != null && !newItems.contains(selectedItem)) {
      selectedItem = null;
    }
    if (selectedItem == null && newItems.contains(newSelectedItemIfSelectionCannotBePreserved)) {
      selectedItem = newSelectedItemIfSelectionCannotBePreserved;
    }
    comboBox.removeAllItems();
    for (T newItem : newItems) {
      comboBox.addItem(newItem);
    }
    if (selectedItem != null) {
      int count = comboBox.getItemCount();
      for (int i = 0; i < count; i++) {
        Object item = comboBox.getItemAt(i);
        if (selectedItem.equals(item)) {
          comboBox.setSelectedIndex(i);
          break;
        }
      }
    }
  }

  private static <T> boolean shouldUpdate(@NotNull JComboBox comboBox, @NotNull List<T> newItems) {
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
                                           @NotNull final NotNullProducer<List<String>> historyProvider) {
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
    Set<String> newHistorySet = ContainerUtil.newHashSet(history);
    List<String> prevHistory = textFieldWithHistory.getHistory();
    List<String> mergedHistory = ContainerUtil.newArrayList();
    if (mergeWithPrevHistory) {
      for (String item : prevHistory) {
        if (!newHistorySet.contains(item)) {
          mergedHistory.add(item);
        }
      }
    }
    else {
      String currentText = textFieldWithHistory.getText();
      if (StringUtil.isNotEmpty(currentText) && !newHistorySet.contains(currentText)) {
        mergedHistory.add(currentText);
      }
    }
    mergedHistory.addAll(history);
    textFieldWithHistory.setHistory(mergedHistory);
    setLongestAsPrototype(textFieldWithHistory, mergedHistory);
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
    for (String s : variants) {
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
                                                          @NotNull String browseDialogTitle,
                                                          @NotNull FileChooserDescriptor fileChooserDescriptor) {
    doInstall(project,
              textFieldWithHistoryWithBrowseButton,
              textFieldWithHistoryWithBrowseButton.getChildComponent().getTextEditor(),
              browseDialogTitle,
              fileChooserDescriptor,
              TextComponentAccessor.TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT);
  }

  public static void installFileCompletionAndBrowseDialog(@Nullable Project project,
                                                          @NotNull TextFieldWithBrowseButton textFieldWithBrowseButton,
                                                          @NotNull String browseDialogTitle,
                                                          @NotNull FileChooserDescriptor fileChooserDescriptor) {
    doInstall(project,
              textFieldWithBrowseButton,
              textFieldWithBrowseButton.getTextField(),
              browseDialogTitle,
              fileChooserDescriptor,
              TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
  }

  public static <T extends JComponent> void doInstall(@Nullable Project project,
                                                       @NotNull ComponentWithBrowseButton<T> componentWithBrowseButton,
                                                       @NotNull JTextField textField,
                                                       @NotNull String browseDialogTitle,
                                                       @NotNull FileChooserDescriptor fileChooserDescriptor,
                                                       @NotNull TextComponentAccessor<T> textComponentAccessor) {
    fileChooserDescriptor = fileChooserDescriptor.withShowHiddenFiles(SystemInfo.isUnix);
    componentWithBrowseButton.addBrowseFolderListener(
      project,
      new ComponentWithBrowseButton.BrowseFolderActionListener<T>(
        browseDialogTitle,
        null,
        componentWithBrowseButton,
        project,
        fileChooserDescriptor,
        textComponentAccessor
      ),
      true
    );
    FileChooserFactory.getInstance().installFileCompletion(
      textField,
      fileChooserDescriptor,
      true,
      project
    );
  }

  @NotNull
  public static HyperlinkLabel createWebHyperlink(@NotNull String url) {
    return createWebHyperlink(url, url);
  }

  @NotNull
  public static HyperlinkLabel createWebHyperlink(@NotNull String text, @NotNull String url) {
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
        public void setText(String t) {
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
      textPane.setFont(myFont != null ? myFont : UIUtil.getLabelFont());
      textPane.setContentType(UIUtil.HTML_MIME);
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
  public static JEditorPane createHtmlViewer(boolean carryTextOver,
                                             @Nullable Font font,
                                             @Nullable Color background,
                                             @Nullable Color foreground) {
    final JEditorPane textPane;
    if (carryTextOver) {
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
    textPane.setFont(font != null ? font : UIUtil.getLabelFont());
    textPane.setContentType(UIUtil.HTML_MIME);
    textPane.setEditable(false);
    if (background != null) {
      textPane.setBackground(background);
    }
    else {
      textPane.setOpaque(false);
    }
    textPane.setForeground(foreground != null ? foreground : UIUtil.getLabelForeground());
    textPane.setFocusable(false);
    return textPane;
  }

  public static void setHtml(@NotNull JEditorPane editorPane,
                             @NotNull String bodyInnerHtml,
                             @Nullable Color foregroundColor) {
    String html = String.format(
      "<html><head>%s</head><body>%s</body></html>",
      UIUtil.getCssFontDeclaration(editorPane.getFont(), foregroundColor, null, null),
      bodyInnerHtml
    );
    editorPane.setText(html);
  }

  @NotNull
  public static TextFieldWithHistoryWithBrowseButton createTextFieldWithHistoryWithBrowseButton(@Nullable Project project,
                                                                                                @NotNull String browseDialogTitle,
                                                                                                @NotNull FileChooserDescriptor fileChooserDescriptor,
                                                                                                @Nullable NotNullProducer<List<String>> historyProvider) {
    TextFieldWithHistoryWithBrowseButton textFieldWithHistoryWithBrowseButton = new TextFieldWithHistoryWithBrowseButton();
    TextFieldWithHistory textFieldWithHistory = textFieldWithHistoryWithBrowseButton.getChildComponent();
    textFieldWithHistory.setHistorySize(-1);
    textFieldWithHistory.setMinimumAndPreferredWidth(0);
    if (historyProvider != null) {
      addHistoryOnExpansion(textFieldWithHistory, historyProvider);
    }
    installFileCompletionAndBrowseDialog(
      project,
      textFieldWithHistoryWithBrowseButton,
      browseDialogTitle,
      fileChooserDescriptor
    );
    return textFieldWithHistoryWithBrowseButton;
  }

  @NotNull
  public static <C extends JComponent> ComponentWithBrowseButton<C> wrapWithInfoButton(@NotNull final C component,
                                                                                       @NotNull String infoButtonTooltip,
                                                                                       @NotNull ActionListener listener) {
    ComponentWithBrowseButton<C> comp = new ComponentWithBrowseButton<C>(component, listener);
    FixedSizeButton uiHelpButton = comp.getButton();
    uiHelpButton.setToolTipText(infoButtonTooltip);
    uiHelpButton.setIcon(UIUtil.getBalloonInformationIcon());
    uiHelpButton.setHorizontalAlignment(SwingConstants.CENTER);
    uiHelpButton.setVerticalAlignment(SwingConstants.CENTER);
    return comp;
  }

  private static class CopyLinkAction extends AnAction {

    private final String myUrl;

    private CopyLinkAction(@NotNull String url) {
      super("Copy Link Address", null, PlatformIcons.COPY_ICON);
      myUrl = url;
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(true);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Transferable content = new StringSelection(myUrl);
      CopyPasteManager.getInstance().setContents(content);
    }
  }

  private static class OpenLinkInBrowser extends AnAction {

    private final String myUrl;

    private OpenLinkInBrowser(@NotNull String url) {
      super("Open Link in Browser", null, PlatformIcons.WEB_ICON);
      myUrl = url;
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(true);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      BrowserUtil.browse(myUrl);
    }
  }
}