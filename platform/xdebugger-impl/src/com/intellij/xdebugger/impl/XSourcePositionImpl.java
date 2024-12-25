// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NonNavigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.DocumentUtil;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class XSourcePositionImpl implements XSourcePosition {
  private final VirtualFile myFile;

  private XSourcePositionImpl(@NotNull VirtualFile file) {
    myFile = file;
  }

  @Override
  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  /**
   * do not call this method from plugins, use {@link XDebuggerUtil#createPositionByOffset(VirtualFile, int)} instead
   */
  public static @Nullable XSourcePositionImpl createByOffset(@Nullable VirtualFile file, final int offset) {
    if (file == null) return null;

    return new XSourcePositionImpl(file) {
      private final NotNullLazyValue<Integer> myLine = NotNullLazyValue.atomicLazy(() -> {
        return ReadAction.compute(() -> {
          Document document = FileDocumentManager.getInstance().getDocument(file);
          if (document == null) {
            return -1;
          }
          return DocumentUtil.isValidOffset(offset, document) ? document.getLineNumber(offset) : -1;
        });
      });

      @Override
      public int getLine() {
        return myLine.getValue();
      }

      @Override
      public int getOffset() {
        return offset;
      }
    };
  }

  /**
   * do not call this method from plugins, use {@link XDebuggerUtil#createPositionByElement(PsiElement)} instead
   */
  public static @Nullable XSourcePositionImpl createByElement(@Nullable PsiElement element) {
    if (element == null) return null;

    PsiFile psiFile = element.getContainingFile();
    if (psiFile == null) return null;

    final VirtualFile file = psiFile.getVirtualFile();
    if (file == null) return null;

    final SmartPsiElementPointer<PsiElement> pointer =
      SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);

    return new XSourcePositionImpl(file) {
      private final NotNullLazyValue<XSourcePosition> myDelegate = NotNullLazyValue.atomicLazy(() -> {
        return ReadAction.compute(() -> {
          PsiElement elem = pointer.getElement();
          return XSourcePositionImpl.createByOffset(pointer.getVirtualFile(), elem != null ? elem.getTextOffset() : -1);
        });
      });

      @Override
      public int getLine() {
        return myDelegate.getValue().getLine();
      }

      @Override
      public int getOffset() {
        return myDelegate.getValue().getOffset();
      }

      @Override
      public @NotNull Navigatable createNavigatable(@NotNull Project project) {
        // no need to create delegate here, it may be expensive
        if (myDelegate.isComputed()) {
          return myDelegate.getValue().createNavigatable(project);
        }
        PsiElement elem = pointer.getElement();
        if (elem instanceof Navigatable) {
          return ((Navigatable)elem);
        }
        return NonNavigatable.INSTANCE;
      }
    };
  }

  /**
   * do not call this method from plugins, use {@link XDebuggerUtil#createPosition(VirtualFile, int)} instead
   */
  @Contract("null , _ -> null; !null, _ -> !null")
  public static XSourcePositionImpl create(@Nullable VirtualFile file, int line) {
    return file == null ? null : create(file, line, 0);
  }

  /**
   * do not call this method from plugins, use {@link XDebuggerUtil#createPosition(VirtualFile, int, int)} instead
   */
  @Contract("null , _, _ -> null; !null, _, _ -> !null")
  public static XSourcePositionImpl create(@Nullable VirtualFile file, final int line, final int column) {
    if (file == null) {
      return null;
    }

    return new XSourcePositionImpl(file) {
      private final NotNullLazyValue<Integer> myOffset = NotNullLazyValue.atomicLazy(() -> {
        return ReadAction.compute(() -> {
          int offset;
          if (file instanceof LightVirtualFile || file instanceof HttpVirtualFile) {
            return -1;
          }
          else {
            Document document = FileDocumentManager.getInstance().getDocument(file);
            if (document == null) {
              return -1;
            }
            int l = Math.max(0, line);
            int c = Math.max(0, column);

            offset = l < document.getLineCount() ? document.getLineStartOffset(l) + c : -1;

            if (offset >= document.getTextLength()) {
              offset = document.getTextLength() - 1;
            }
          }
          return offset;
        });
      });

      @Override
      public int getLine() {
        return line;
      }

      @Override
      public int getOffset() {
        return myOffset.getValue();
      }
    };
  }

  @Override
  public @NotNull Navigatable createNavigatable(@NotNull Project project) {
    return XDebuggerUtilImpl.createNavigatable(project, this);
  }

  @Override
  public String toString() {
    return "XSourcePositionImpl[" + myFile + ":" + getLine() + "(" + getOffset() + ")]";
  }
}
