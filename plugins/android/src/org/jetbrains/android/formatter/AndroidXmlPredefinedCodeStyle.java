package org.jetbrains.android.formatter;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.PredefinedCodeStyle;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidXmlPredefinedCodeStyle extends PredefinedCodeStyle {
  public AndroidXmlPredefinedCodeStyle() {
    super("Android", XMLLanguage.INSTANCE);
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    final CommonCodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptions(XmlFileType.INSTANCE);
    indentOptions.CONTINUATION_INDENT_SIZE = indentOptions.INDENT_SIZE;

    XmlCodeStyleSettings xmlSettings = settings.getCustomSettings(XmlCodeStyleSettings.class);
    xmlSettings.XML_ALIGN_ATTRIBUTES = false;
    xmlSettings.XML_SPACE_INSIDE_EMPTY_TAG = true;
    xmlSettings.XML_KEEP_LINE_BREAKS = false;

    final AndroidXmlCodeStyleSettings androidSettings = AndroidXmlCodeStyleSettings.getInstance(settings);
    androidSettings.USE_CUSTOM_SETTINGS = true;

    androidSettings.LAYOUT_SETTINGS = new AndroidXmlCodeStyleSettings.LayoutSettings();
    androidSettings.MANIFEST_SETTINGS = new AndroidXmlCodeStyleSettings.ManifestSettings();
    androidSettings.VALUE_RESOURCE_FILE_SETTINGS = new AndroidXmlCodeStyleSettings.ValueResourceFileSettings();
    androidSettings.OTHER_SETTINGS = new AndroidXmlCodeStyleSettings.OtherSettings();
  }
}
