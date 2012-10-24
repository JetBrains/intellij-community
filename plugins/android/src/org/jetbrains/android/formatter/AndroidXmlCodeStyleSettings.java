package org.jetbrains.android.formatter;

import com.intellij.formatting.FormattingDocumentModel;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlPolicy;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.android.dom.resources.Style;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidXmlCodeStyleSettings extends CustomCodeStyleSettings {
  public boolean USE_CUSTOM_SETTINGS = false;

  public LayoutSettings LAYOUT_SETTINGS = new LayoutSettings();
  public ManifestSettings MANIFEST_SETTINGS = new ManifestSettings();
  public ValueResourceFileSettings VALUE_RESOURCE_FILE_SETTINGS = new ValueResourceFileSettings();
  public OtherSettings OTHER_SETTINGS = new OtherSettings();

  public AndroidXmlCodeStyleSettings(CodeStyleSettings container) {
    super("AndroidXmlCodeStyleSettings", container);
  }

  public static AndroidXmlCodeStyleSettings getInstance(CodeStyleSettings settings) {
    return settings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
  }

  @Override
  public Object clone() {
    try {
      final AndroidXmlCodeStyleSettings cloned = (AndroidXmlCodeStyleSettings)super.clone();
      cloned.LAYOUT_SETTINGS = (LayoutSettings)LAYOUT_SETTINGS.clone();
      cloned.MANIFEST_SETTINGS = (ManifestSettings)MANIFEST_SETTINGS.clone();
      cloned.VALUE_RESOURCE_FILE_SETTINGS = (ValueResourceFileSettings)VALUE_RESOURCE_FILE_SETTINGS.clone();
      cloned.OTHER_SETTINGS = (OtherSettings)OTHER_SETTINGS.clone();
      return cloned;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  public static class MySettings implements JDOMExternalizable, Cloneable {
    public int WRAP_ATTRIBUTES;
    public boolean INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE;

    @Override
    public void readExternal(Element element) throws InvalidDataException {
      XmlSerializer.deserializeInto(this, element);
    }

    @Override
    public void writeExternal(Element element) throws WriteExternalException {
      XmlSerializer.serializeInto(this, element, new SkipDefaultValuesSerializationFilters());
    }

    public XmlPolicy createXmlPolicy(CodeStyleSettings settings, FormattingDocumentModel documentModel) {
      return new AndroidXmlPolicy(settings, this, documentModel);
    }

    @Override
    protected MySettings clone() throws CloneNotSupportedException {
      try {
        return (MySettings)super.clone();
      }
      catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final MySettings s = (MySettings)o;

      return INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE == s.INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE &&
             WRAP_ATTRIBUTES == s.WRAP_ATTRIBUTES;
    }

    @Override
    public int hashCode() {
      int result = WRAP_ATTRIBUTES;
      result = 31 * result + (INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE ? 1 : 0);
      return result;
    }
  }

  public static class LayoutSettings extends MySettings {
    public boolean INSERT_BLANK_LINE_BEFORE_TAG = true;

    {
      WRAP_ATTRIBUTES = CommonCodeStyleSettings.WRAP_ALWAYS;
      INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE = true;
    }

    @Override
    public XmlPolicy createXmlPolicy(CodeStyleSettings settings, FormattingDocumentModel documentModel) {
      return new AndroidXmlPolicy(settings, this, documentModel) {
        @Override
        public boolean insertLineBreakBeforeTag(XmlTag xmlTag) {
          return INSERT_BLANK_LINE_BEFORE_TAG;
        }

        @Override
        public boolean insertLineBreakAfterTagBegin(XmlTag tag) {
          return INSERT_BLANK_LINE_BEFORE_TAG;
        }
      };
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      final LayoutSettings settings = (LayoutSettings)o;

      return INSERT_BLANK_LINE_BEFORE_TAG == settings.INSERT_BLANK_LINE_BEFORE_TAG;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (INSERT_BLANK_LINE_BEFORE_TAG ? 1 : 0);
      return result;
    }
  }

  public static class ManifestSettings extends MySettings {
    public boolean GROUP_TAGS_WITH_SAME_NAME = true;

    {
      WRAP_ATTRIBUTES = CommonCodeStyleSettings.WRAP_ALWAYS;
      INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE = true;
    }

    @Override
    public XmlPolicy createXmlPolicy(CodeStyleSettings settings, FormattingDocumentModel documentModel) {
      return new AndroidXmlPolicy(settings, this, documentModel) {
        @Override
        public boolean insertLineBreakBeforeTag(XmlTag xmlTag) {
          if (GROUP_TAGS_WITH_SAME_NAME) {
            PsiElement element = getPrevSiblingElement(xmlTag);

            if (element instanceof XmlTag) {
              final String name1 = ((XmlTag)element).getName();
              final String name2 = xmlTag.getName();

              if (!name1.equals(name2)) {
                element = getPrevSiblingElement(element);

                if (element instanceof XmlTag && ((XmlTag)element).getName().equals(name1)) {
                  return true;
                }
                element = getNextSiblingElement(xmlTag);
                return element instanceof XmlTag && ((XmlTag)element).getName().equals(name2);
              }
            }
          }
          return false;
        }

        @Override
        public boolean insertLineBreakAfterTagBegin(XmlTag tag) {
          return GROUP_TAGS_WITH_SAME_NAME && tag.getParentTag() == null;
        }
      };
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      final ManifestSettings settings = (ManifestSettings)o;

      return GROUP_TAGS_WITH_SAME_NAME == settings.GROUP_TAGS_WITH_SAME_NAME;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (GROUP_TAGS_WITH_SAME_NAME ? 1 : 0);
      return result;
    }
  }

  public static class ValueResourceFileSettings extends MySettings {
    public boolean INSERT_LINE_BREAKS_AROUND_STYLE = true;

    {
      WRAP_ATTRIBUTES = CommonCodeStyleSettings.DO_NOT_WRAP;
      INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE = false;
    }

    @Override
    public XmlPolicy createXmlPolicy(CodeStyleSettings settings, FormattingDocumentModel documentModel) {
      return new AndroidXmlPolicy(settings, this, documentModel) {
        @Override
        public boolean insertLineBreakAfterTagBegin(XmlTag tag) {
          if (!INSERT_LINE_BREAKS_AROUND_STYLE) {
            return false;
          }
          final XmlTag[] subTags = tag.getSubTags();
          return subTags.length != 0 && isStyleTag(subTags[0]);
        }

        @Override
        public boolean insertLineBreakBeforeTag(XmlTag xmlTag) {
          if (!INSERT_LINE_BREAKS_AROUND_STYLE) {
            return false;
          }
          if (isStyleTag(xmlTag)) {
            return true;
          }
          final PsiElement sibling = getPrevSiblingElement(xmlTag);
          return sibling instanceof XmlTag && isStyleTag((XmlTag)sibling);
        }

        private boolean isStyleTag(XmlTag tag) {
          return DomManager.getDomManager(tag.getProject()).
            getDomElement(tag) instanceof Style;
        }
      };
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      final ValueResourceFileSettings settings = (ValueResourceFileSettings)o;

      return INSERT_LINE_BREAKS_AROUND_STYLE == settings.INSERT_LINE_BREAKS_AROUND_STYLE;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (INSERT_LINE_BREAKS_AROUND_STYLE ? 1 : 0);
      return result;
    }
  }

  public static class OtherSettings extends MySettings {
    {
      WRAP_ATTRIBUTES = CommonCodeStyleSettings.WRAP_ALWAYS;
      INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE = true;
    }
  }
}
