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
import com.intellij.util.ThreeState;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.hints.BaseFileTypeInputFilter;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.xml.index.XmlIndex;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.util.indexing.hints.BaseFileTypeInputFilter.FileTypeStrategy.BEFORE_SUBSTITUTION;

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
    return new BaseFileTypeInputFilter(BEFORE_SUBSTITUTION) {
      @Override
      public boolean whenAllOtherHintsUnsure(@NotNull IndexedFile file) {
        return file.getFile().isInLocalFileSystem();
      }

      @Override
      public @NotNull ThreeState acceptFileType(@NotNull FileType fileType) {
        if (fileType == HtmlFileType.INSTANCE) {
          return ThreeState.UNSURE; // check if a file is in local filesystem.
        }
        if (fileType instanceof LanguageFileType && ((LanguageFileType)fileType).getLanguage() instanceof TemplateLanguage) {
          return ThreeState.UNSURE; // check if a file is in local filesystem.
        }
        return ThreeState.NO;
      }
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
