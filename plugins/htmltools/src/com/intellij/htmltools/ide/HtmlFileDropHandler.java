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
package com.intellij.htmltools.ide;

import com.intellij.codeInsight.editorActions.XmlEditUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.htmltools.HtmlToolsBundle;
import com.intellij.htmltools.xml.util.HtmlReferenceProvider;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.impl.source.html.HtmlFileImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.ui.LightweightHint;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.HtmlUtil;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.util.ImageInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.text.MessageFormat;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public final class HtmlFileDropHandler extends CustomFileDropHandler {
  @Override
  public boolean canHandle(@NotNull Transferable t, @Nullable Editor editor) {
    if (editor == null) return false;
    final VirtualFile file = getDroppedFile(t);
    if (file == null) return false;
    final FileType type = file.getFileType();
    return isImage(type) || isCSS(type) || isJS(type);
  }

  @Override
  public boolean handleDrop(@NotNull Transferable t, @Nullable Editor editor, Project project) {
    assert editor != null;
    final Document document = editor.getDocument();
    final PsiFile target = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (!(target instanceof HtmlFileImpl)) return false;

    final VirtualFile file = getDroppedFile(t);
    if (file == null) return false;
    final FileType type = file.getFileType();
    if (isCSS(type)) {
      return insertTagInHead((HtmlFileImpl)target, file, editor, "<link rel={0}stylesheet{0} href={0}{1}{0}>");
    }
    if (isJS(type)) {
      return insertTagInHead((HtmlFileImpl)target, file, editor, "<script src={0}{1}{0}></script>");
    }
    if (isImage(type)) {
      return insertImageTag((HtmlFileImpl)target, file, editor, "<img src={0}{1}{0}/>");
    }
    return false;
  }

  private static boolean insertTagInHead(HtmlFileImpl target, VirtualFile droppedFile, Editor editor, String tagTemplate) {
    final String relativePath = getRelativePath(target, droppedFile);
    final String tagText = prepareTagText(target, tagTemplate, relativePath);
    final XmlDocument document = target.getDocument();
    if (document == null || tagText == null) return false;
    final XmlTag[] topLevelTags = PsiTreeUtil.getChildrenOfType(document, XmlTag.class);
    XmlTag html = null;
    XmlTag head = null;
    if (topLevelTags != null) {
      for (XmlTag tag : topLevelTags) {
        if ("html".equals(tag.getName())) {
          html = tag;
          break;
        }
        if ("head".equals(tag.getName())) {
          head = tag;
          break;
        }
      }
    }
    if (html != null) {
      head = html.findFirstSubTag("head");
    }
    final PsiElement parent = head != null ? head : html != null ? html : document;
    if (checkIfPresent(editor, tagTemplate, relativePath, parent)) return true;
    WriteCommandAction.writeCommandAction(target.getProject(), target).run(() -> {
      final XmlTag tag = XmlElementFactory.getInstance(target.getProject()).createHTMLTagFromText(tagText);
      if (parent instanceof XmlTag) {
        ((XmlTag)parent).addSubTag(tag, !"head".equals(((XmlTag)parent).getName()));
      }
      else {
        parent.addAfter(tag, ((XmlDocument)parent).getProlog());
      }
    });
    return true;
  }

  private static boolean checkIfPresent(Editor editor, String tagTemplate, String relativePath, PsiElement parent) {
    for (PsiElement element : parent.getChildren()) {
      if (element instanceof XmlTag tag) {
        if (tagTemplate.startsWith("<" + tag.getName() + " ")) {
          final String attrName = HtmlUtil.SCRIPT_TAG_NAME.equals(tag.getName()) ? "src" : "href";
          String path = tag.getAttributeValue(attrName);
          if (StringUtil.equals(path, relativePath)) {
            final LogicalPosition position = getPosition(editor, tag, attrName);
            editor.getScrollingModel().scrollTo(position, ScrollType.MAKE_VISIBLE);
            final int flags = HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING;
            final LightweightHint hint = new LightweightHint(HintUtil.createInformationLabel(
              HtmlToolsBundle.message("html.drop.handler.hint.text.file.already.linked", relativePath)));
            final Point point = HintManagerImpl.getHintPosition(hint, editor, position, HintManager.ABOVE);
            HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, point, flags, 0, false);
            return true;
          }
        }
      }
    }
    return false;
  }

  @SuppressWarnings("ConstantConditions")
  private static LogicalPosition getPosition(Editor editor, XmlTag tag, String attrName) {
    final XmlAttributeValue element = tag.getAttribute(attrName).getValueElement();
    return editor.offsetToLogicalPosition(element.getTextRange().getStartOffset() + element.getTextRange().getLength() / 2);
  }

  private static boolean insertImageTag(HtmlFileImpl target, VirtualFile droppedFile, Editor editor, String tagTemplate) {
    final String tagText = prepareTagText(target, tagTemplate, getRelativePath(target, droppedFile));
    final int offset = editor.getCaretModel().getOffset();
    PsiElement element = target.findElementAt(offset);
    if (element == null || tagText == null) return false;
    final XmlElement parent = PsiTreeUtil.getParentOfType(element, XmlTag.class, XmlDocument.class);
    PsiElement candidate = null;
    for (PsiElement psiElement : parent.getChildren()) {
      if (psiElement.getTextRange().getEndOffset() >= offset) {
        candidate = psiElement;
        break;
      }
    }
    if (parent instanceof XmlTag && candidate != null) {
      final ASTNode startEnd = XmlChildRole.START_TAG_END_FINDER.findChild(parent.getNode());
      if (startEnd != null && startEnd.getTextRange().getEndOffset() > candidate.getTextRange().getEndOffset()) {
        candidate = startEnd.getPsi();
      }
      final ASTNode endStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(parent.getNode());
      if (endStart != null && endStart.getTextRange().getStartOffset() <= candidate.getTextRange().getStartOffset()) {
        candidate = endStart.getPsi().getPrevSibling();
      }
    }
    PsiElement insertionPoint = candidate;
    WriteCommandAction.writeCommandAction(target.getProject(), target).run(() -> {
      XmlTag tag = XmlElementFactory.getInstance(target.getProject()).createHTMLTagFromText(tagText);
      tag = (XmlTag)parent.addAfter(tag, insertionPoint);
      final ImageInfo info = HtmlReferenceProvider.SizeReference.getImageInfo(tag);
      if (info != null && info.width != 0 && info.height != 0) {
        tag.setAttribute("height", String.valueOf(info.height));
        tag.setAttribute("width", String.valueOf(info.width));
      }
    });
    return true;
  }

  private static @Nullable String prepareTagText(HtmlFileImpl target, String tagTemplate, String path) {
    return path != null ? MessageFormat.format(tagTemplate, XmlEditUtil.getAttributeQuote(target), path) : null;
  }

  private static String getRelativePath(HtmlFileImpl target, VirtualFile droppedFile) {
    final VirtualFile targetFile = target.getVirtualFile();
    return FileUtil.getRelativePath(targetFile.getParent().getPath(), droppedFile.getPath(), '/');
  }

  private static @Nullable VirtualFile getDroppedFile(@NotNull Transferable t) {
    List<File> list = FileCopyPasteUtil.getFileList(t);
    final File io = list != null && list.size() == 1 ? ContainerUtil.getFirstItem(list) : null;
    return io != null ? VfsUtil.findFileByIoFile(io, true) : null;
  }

  private static boolean isJS(FileType type) {
    return type instanceof LanguageFileType && ((LanguageFileType)type).getLanguage().isKindOf(Language.findLanguageByID("JavaScript"));
  }

  private static boolean isCSS(FileType type) {
    return type instanceof LanguageFileType && ((LanguageFileType)type).getLanguage().isKindOf(Language.findLanguageByID("CSS"));
  }

  private static boolean isImage(FileType type) {
    return type == ImageFileTypeManager.getInstance().getImageFileType();
  }

}
