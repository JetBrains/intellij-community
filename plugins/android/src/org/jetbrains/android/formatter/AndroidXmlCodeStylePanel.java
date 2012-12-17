package org.jetbrains.android.formatter;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
* @author Eugene.Kudelevsky
*/
public class AndroidXmlCodeStylePanel extends CodeStyleAbstractPanel {
  private final JPanel myPanel;
  private final JBCheckBox myUseCustomSettings;
  private final List<MyFileSpecificPanel> myCodeStylePanels;
  private final JPanel myFileSpecificCodeStylesPanel;

  AndroidXmlCodeStylePanel(CodeStyleSettings currentSettings, CodeStyleSettings settings) {
    super(XMLLanguage.INSTANCE, currentSettings, settings);
    myPanel = new JPanel(new BorderLayout());
    JPanel centerPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
    centerPanel.setBorder(IdeBorderFactory.createEmptyBorder(2, 2, 2, 2));
    myPanel.add(centerPanel, BorderLayout.CENTER);
    myUseCustomSettings = new JBCheckBox("Use custom formatting settings for Android XML files");
    myPanel.add(myUseCustomSettings, BorderLayout.NORTH);

    myCodeStylePanels = new ArrayList<MyFileSpecificPanel>();
    myFileSpecificCodeStylesPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
    centerPanel.add(myFileSpecificCodeStylesPanel);

    myUseCustomSettings.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        UIUtil.setEnabled(myFileSpecificCodeStylesPanel, myUseCustomSettings.isSelected(), true);
      }
    });

    myCodeStylePanels.add(new ManifestCodeStylePanel());
    myCodeStylePanels.add(new LayoutCodeStylePanel());
    myCodeStylePanels.add(new ValueResourcesCodeStylePanel());
    myCodeStylePanels.add(new OtherCodeStylePanel());

    for (MyFileSpecificPanel panel : myCodeStylePanels) {
      final JPanel titledPanel = new JPanel(new BorderLayout());
      titledPanel.setBorder(IdeBorderFactory.createTitledBorder(panel.getTitle()));
      titledPanel.add(panel, BorderLayout.CENTER);
      myFileSpecificCodeStylesPanel.add(titledPanel);
    }
  }

  @Override
  protected int getRightMargin() {
    return 0;
  }

  @Nullable
  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    return null;
  }

  @NotNull
  @Override
  protected FileType getFileType() {
    return XmlFileType.INSTANCE;
  }

  @Nullable
  @Override
  protected String getPreviewText() {
    return null;
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    final AndroidXmlCodeStyleSettings androidSettings = AndroidXmlCodeStyleSettings.getInstance(settings);
    androidSettings.USE_CUSTOM_SETTINGS = myUseCustomSettings.isSelected();

    for (MyFileSpecificPanel panel : myCodeStylePanels) {
      panel.apply(androidSettings);
    }
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    final AndroidXmlCodeStyleSettings androidSettings = AndroidXmlCodeStyleSettings.getInstance(settings);

    if (androidSettings.USE_CUSTOM_SETTINGS != myUseCustomSettings.isSelected()) {
      return true;
    }

    for (MyFileSpecificPanel panel : myCodeStylePanels) {
      if (panel.isModified(androidSettings)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  @Override
  public JComponent getPanel() {
    return myPanel;
  }

  @Override
  protected void resetImpl(CodeStyleSettings settings) {
    final AndroidXmlCodeStyleSettings androidSettings = AndroidXmlCodeStyleSettings.getInstance(settings);
    myUseCustomSettings.setSelected(androidSettings.USE_CUSTOM_SETTINGS);
    UIUtil.setEnabled(myFileSpecificCodeStylesPanel, androidSettings.USE_CUSTOM_SETTINGS, true);

    for (MyFileSpecificPanel panel : myCodeStylePanels) {
      panel.resetImpl(androidSettings);
    }
  }

  public abstract static class MyFileSpecificPanel<T extends AndroidXmlCodeStyleSettings.MySettings> extends JPanel {
    private JPanel myPanel;
    private JPanel myAdditionalOptionsPanel;
    private JComboBox myWrapAttributesCombo;

    protected JBCheckBox myInsertLineBreakBeforeFirstAttributeCheckBox;

    private final String myTitle;
    private final ContextSpecificSettingsProviders.Provider<T> mySettingsProvider;

    protected MyFileSpecificPanel(String title, ContextSpecificSettingsProviders.Provider<T> provider) {
      myTitle = title;
      mySettingsProvider = provider;
    }

    protected void init() {
      final JPanel panel = getAdditionalOptionsPanel();

      if (panel != null) {
        myAdditionalOptionsPanel.add(panel, BorderLayout.CENTER);
      }
      else {
        myAdditionalOptionsPanel.setVisible(false);
      }
      setLayout(new BorderLayout());
      add(myPanel, BorderLayout.CENTER);

      fillWrappingCombo(myWrapAttributesCombo);
    }

    @Nullable
    public JPanel getAdditionalOptionsPanel() {
      return null;
    }

    public final void apply(AndroidXmlCodeStyleSettings settings) {
      apply(mySettingsProvider.getSettings(settings));
    }

    protected void apply(T s) {
      s.WRAP_ATTRIBUTES = ourWrappings[myWrapAttributesCombo.getSelectedIndex()];
      s.INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE = myInsertLineBreakBeforeFirstAttributeCheckBox.isSelected();
    }

    public final boolean isModified(AndroidXmlCodeStyleSettings settings) {
      return isModified(mySettingsProvider.getSettings(settings));
    }

    protected boolean isModified(T s) {
      if (s.WRAP_ATTRIBUTES != ourWrappings[myWrapAttributesCombo.getSelectedIndex()]) {
        return true;
      }
      if (s.INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE != myInsertLineBreakBeforeFirstAttributeCheckBox.isSelected()) {
        return true;
      }
      return false;
    }

    protected final void resetImpl(AndroidXmlCodeStyleSettings settings) {
      resetImpl(mySettingsProvider.getSettings(settings));
    }

    protected void resetImpl(T s) {
      myWrapAttributesCombo.setSelectedIndex(getIndexForWrapping(s.WRAP_ATTRIBUTES));
      myInsertLineBreakBeforeFirstAttributeCheckBox.setSelected(s.INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE);
    }

    @NotNull
    public String getTitle() {
      return myTitle;
    }
  }

  private static class LayoutCodeStylePanel extends MyFileSpecificPanel<AndroidXmlCodeStyleSettings.LayoutSettings> {
    private JPanel myPanel;
    private JBCheckBox myInsertNewLineBeforeTagCheckBox;

    public LayoutCodeStylePanel() {
      super("Layout Files", ContextSpecificSettingsProviders.LAYOUT);
      init();
    }

    @Nullable
    @Override
    public JPanel getAdditionalOptionsPanel() {
      return myPanel;
    }

    @Override
    protected boolean isModified(AndroidXmlCodeStyleSettings.LayoutSettings s) {
      if (super.isModified(s)) {
        return true;
      }
      return myInsertNewLineBeforeTagCheckBox.isSelected() != s.INSERT_BLANK_LINE_BEFORE_TAG;
    }

    @Override
    protected void resetImpl(AndroidXmlCodeStyleSettings.LayoutSettings s) {
      super.resetImpl(s);
      myInsertNewLineBeforeTagCheckBox.setSelected(s.INSERT_BLANK_LINE_BEFORE_TAG);
    }

    @Override
    protected void apply(AndroidXmlCodeStyleSettings.LayoutSettings s) {
      super.apply(s);
      s.INSERT_BLANK_LINE_BEFORE_TAG = myInsertNewLineBeforeTagCheckBox.isSelected();
    }
  }

  public static class ManifestCodeStylePanel extends MyFileSpecificPanel<AndroidXmlCodeStyleSettings.ManifestSettings> {
    private final JBCheckBox myGroupTagsCheckBox;
    private JPanel myPanel;

    public ManifestCodeStylePanel() {
      super("AndroidManifest.xml", ContextSpecificSettingsProviders.MANIFEST);

      myPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
      myGroupTagsCheckBox = new JBCheckBox("Group tags with the same name");
      myPanel.add(myGroupTagsCheckBox);

      init();
    }

    @Nullable
    @Override
    public JPanel getAdditionalOptionsPanel() {
      return myPanel;
    }

    @Override
    protected void apply(AndroidXmlCodeStyleSettings.ManifestSettings s) {
      super.apply(s);
      s.GROUP_TAGS_WITH_SAME_NAME = myGroupTagsCheckBox.isSelected();
    }

    @Override
    protected boolean isModified(AndroidXmlCodeStyleSettings.ManifestSettings s) {
      if (super.isModified(s)) {
        return true;
      }
      return s.GROUP_TAGS_WITH_SAME_NAME != myGroupTagsCheckBox.isSelected();
    }

    @Override
    protected void resetImpl(AndroidXmlCodeStyleSettings.ManifestSettings s) {
      super.resetImpl(s);
      myGroupTagsCheckBox.setSelected(s.GROUP_TAGS_WITH_SAME_NAME);
    }
  }

  public static class ValueResourcesCodeStylePanel extends MyFileSpecificPanel<AndroidXmlCodeStyleSettings.ValueResourceFileSettings> {
    private final JBCheckBox myInsertLineBreaksAroundStyleCheckBox;
    private JPanel myPanel;

    public ValueResourcesCodeStylePanel() {
      super("Value Resource Files and Selectors", ContextSpecificSettingsProviders.VALUE_RESOURCE_FILE);
      myPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
      myInsertLineBreaksAroundStyleCheckBox = new JBCheckBox("Insert line breaks around style declaration");
      myPanel.add(myInsertLineBreaksAroundStyleCheckBox);

      init();
      myInsertLineBreakBeforeFirstAttributeCheckBox.setVisible(false);
    }

    @Nullable
    @Override
    public JPanel getAdditionalOptionsPanel() {
      return myPanel;
    }

    @Override
    protected void apply(AndroidXmlCodeStyleSettings.ValueResourceFileSettings s) {
      super.apply(s);
      s.INSERT_LINE_BREAKS_AROUND_STYLE = myInsertLineBreaksAroundStyleCheckBox.isSelected();
    }

    @Override
    protected boolean isModified(AndroidXmlCodeStyleSettings.ValueResourceFileSettings s) {
      if (super.isModified(s)) {
        return true;
      }
      return s.INSERT_LINE_BREAKS_AROUND_STYLE != myInsertLineBreaksAroundStyleCheckBox.isSelected();
    }

    @Override
    protected void resetImpl(AndroidXmlCodeStyleSettings.ValueResourceFileSettings s) {
      super.resetImpl(s);
      myInsertLineBreaksAroundStyleCheckBox.setSelected(s.INSERT_LINE_BREAKS_AROUND_STYLE);
    }
  }

  public static class OtherCodeStylePanel extends MyFileSpecificPanel<AndroidXmlCodeStyleSettings.OtherSettings> {
    public OtherCodeStylePanel() {
      super("Other XML resource files", ContextSpecificSettingsProviders.OTHER);
      init();
      myInsertLineBreakBeforeFirstAttributeCheckBox.setVisible(false);
    }
  }
}
