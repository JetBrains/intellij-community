package com.intellij.htmltools.html.include;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.include.FileIncludeInfo;
import com.intellij.psi.impl.include.FileIncludeProvider;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Consumer;
import com.intellij.util.indexing.FileContent;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

public final class HtmlFileIncludeProvider extends FileIncludeProvider {
  @Override
  public @NotNull String getId() {
    return "html";
  }

  @Override
  public boolean acceptFile(@NotNull VirtualFile file) {
    return HtmlUtil.isHtmlFile(file);
  }

  @Override
  public void registerFileTypesUsedForIndexing(@NotNull Consumer<? super FileType> fileTypeSink) {
    fileTypeSink.consume(HtmlFileType.INSTANCE);
    fileTypeSink.consume(XHtmlFileType.INSTANCE);
  }

  @Override
  public FileIncludeInfo @NotNull [] getIncludeInfos(@NotNull FileContent content) {
    PsiFile psiFile = content.getPsiFile();
    if (psiFile instanceof XmlFile) {
      return getIncludeInfos((XmlFile)psiFile);
    }
    return FileIncludeInfo.EMPTY;
  }

  public static FileIncludeInfo[] getIncludeInfos(XmlFile xmlFile) {
    return HtmlUtil.getIncludedPathsElements(xmlFile).stream()
      .filter(el -> el != null && !StringUtil.isEmptyOrSpaces(el.getValue()))
      .map(el -> new FileIncludeInfo(el.getValue())).toArray(FileIncludeInfo[]::new);
  }
}
