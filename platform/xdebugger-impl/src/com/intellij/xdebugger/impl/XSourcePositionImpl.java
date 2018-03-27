/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class XSourcePositionImpl implements XSourcePosition {
  private final VirtualFile myFile;

  private XSourcePositionImpl(@NotNull VirtualFile file) {
    myFile = file;
  }

  @Override
  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  /**
   * do not call this method from plugins, use {@link XDebuggerUtil#createPositionByOffset(VirtualFile, int)} instead
   */
  @Nullable
  public static XSourcePositionImpl createByOffset(@Nullable VirtualFile file, final int offset) {
    if (file == null) return null;

    return new XSourcePositionImpl(file) {
      private final AtomicNotNullLazyValue<Integer> myLine = new AtomicNotNullLazyValue<Integer>() {
        @NotNull
        @Override
        protected Integer compute() {
          return ReadAction.compute(() -> {
            Document document = FileDocumentManager.getInstance().getDocument(file);
            if (document == null) {
              return -1;
            }
            return DocumentUtil.isValidOffset(offset, document) ? document.getLineNumber(offset) : -1;
          });
        }
      };

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
  @Nullable
  public static XSourcePositionImpl createByElement(@Nullable PsiElement element) {
    if (element == null) return null;

    PsiFile psiFile = element.getContainingFile();
    if (psiFile == null) return null;

    final VirtualFile file = psiFile.getVirtualFile();
    if (file == null) return null;

    final SmartPsiElementPointer<PsiElement> pointer =
      SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);

    return new XSourcePositionImpl(file) {
      private final AtomicNotNullLazyValue<XSourcePosition> myDelegate = new AtomicNotNullLazyValue<XSourcePosition>() {
        @NotNull
        @Override
        protected XSourcePosition compute() {
          return ReadAction.compute(() -> {
            PsiElement elem = pointer.getElement();
            return XSourcePositionImpl.createByOffset(pointer.getVirtualFile(), elem != null ? elem.getTextOffset() : -1);
          });
        }
      };

      @Override
      public int getLine() {
        return myDelegate.getValue().getLine();
      }

      @Override
      public int getOffset() {
        return myDelegate.getValue().getOffset();
      }

      @NotNull
      @Override
      public Navigatable createNavigatable(@NotNull Project project) {
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
  @Nullable
  public static XSourcePositionImpl create(@Nullable VirtualFile file, int line) {
    return create(file, line, 0);
  }

  /**
   * do not call this method from plugins, use {@link XDebuggerUtil#createPosition(VirtualFile, int, int)} instead
   */
  @Nullable
  public static XSourcePositionImpl create(@Nullable VirtualFile file, final int line, final int column) {
    if (file == null) {
      return null;
    }

    return new XSourcePositionImpl(file) {
      private final AtomicNotNullLazyValue<Integer> myOffset = new AtomicNotNullLazyValue<Integer>() {
        @NotNull
        @Override
        protected Integer compute() {
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
        }
      };

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
  @NotNull
  public Navigatable createNavigatable(@NotNull Project project) {
    return doCreateOpenFileDescriptor(project, this);
  }

  @NotNull
  public static OpenFileDescriptor createOpenFileDescriptor(@NotNull Project project, @NotNull XSourcePosition position) {
    Navigatable navigatable = position.createNavigatable(project);
    if (navigatable instanceof OpenFileDescriptor) {
      return (OpenFileDescriptor)navigatable;
    }
    else {
      return doCreateOpenFileDescriptor(project, position);
    }
  }

  @NotNull
  public static OpenFileDescriptor doCreateOpenFileDescriptor(@NotNull Project project, @NotNull XSourcePosition position) {
    return position.getOffset() != -1
           ? new OpenFileDescriptor(project, position.getFile(), position.getOffset())
           : new OpenFileDescriptor(project, position.getFile(), position.getLine(), 0);
  }

  @Override
  public String toString() {
    return "XSourcePositionImpl[" + myFile + ":" + getLine() + "(" + getOffset() + ")]";
  }
}
