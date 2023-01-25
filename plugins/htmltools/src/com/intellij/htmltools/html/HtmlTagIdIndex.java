package com.intellij.htmltools.html;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.lang.html.HtmlCompatibleFile;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementWalkingVisitor;
import com.intellij.psi.templateLanguages.TemplateLanguage;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.xml.index.XmlIndex;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HtmlTagIdIndex extends XmlIndex<Integer> {
  public static final ID<String, Integer> INDEX = ID.create("HtmlTagIdIndex");

  @NotNull
  @Override
  public ID<String, Integer> getName() {
    return INDEX;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return file -> {
      if (!file.isInLocalFileSystem()) {
        return false;
      }
      FileType fileType = file.getFileType();

      if (fileType == HtmlFileType.INSTANCE) return true;

      return fileType instanceof LanguageFileType && ((LanguageFileType)fileType).getLanguage() instanceof TemplateLanguage;
    };
  }

  @NotNull
  @Override
  public DataIndexer<String, Integer, FileContent> getIndexer() {
    return new DataIndexer<>() {
      @NotNull
      @Override
      public Map<String, Integer> map(@NotNull FileContent inputData) {
        Map<String, Integer> result = new HashMap<>();
        inputData
          .getPsiFile()
          .getViewProvider()
          .getAllFiles()
          .forEach(root -> processPsiFile(result, root));
        return result;
      }

      private void processPsiFile(Map<String, Integer> result, PsiFile file) {
        if (file instanceof HtmlCompatibleFile) {
          new XmlRecursiveElementWalkingVisitor() {
            @Override
            public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
              super.visitXmlAttribute(attribute);
              if ("id".equals(attribute.getName())) {
                String value = attribute.getValue();
                XmlAttributeValue valueElement = attribute.getValueElement();
                if (valueElement != null && StringUtil.isNotEmpty(value)) {
                  result.put(value, valueElement.getTextRange().getStartOffset() + attribute.getValueTextRange().getStartOffset());
                }
              }
            }
          }.visitFile(file);
        }
      }
    };
  }

  @Override
  public int getVersion() {
    return 3;
  }

  @NotNull
  @Override
  public DataExternalizer<Integer> getValueExternalizer() {
    return new DataExternalizer<>() {
      @Override
      public void save(@NotNull DataOutput out, Integer value) throws IOException {
        DataInputOutputUtil.writeINT(out, value);
      }

      @Override
      public Integer read(@NotNull DataInput in) throws IOException {
        return DataInputOutputUtil.readINT(in);
      }
    };
  }
}
