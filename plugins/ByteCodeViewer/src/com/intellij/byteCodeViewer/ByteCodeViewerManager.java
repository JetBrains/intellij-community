// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.byteCodeViewer;

import com.intellij.codeInsight.documentation.DockablePopupManager;
import com.intellij.ide.util.JavaAnonymousClassesHelper;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.util.Textifier;
import org.jetbrains.org.objectweb.asm.util.TraceClassVisitor;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author anna
 */
public class ByteCodeViewerManager extends DockablePopupManager<ByteCodeViewerComponent> {
  private static final ExtensionPointName<ClassSearcher> CLASS_SEARCHER_EP = ExtensionPointName.create("ByteCodeViewer.classSearcher");

  private static final Logger LOG = Logger.getInstance(ByteCodeViewerManager.class);

  private static final String TOOLWINDOW_ID = "Byte Code Viewer";
  private static final String SHOW_BYTECODE_IN_TOOL_WINDOW = "BYTE_CODE_TOOL_WINDOW";
  private static final String BYTECODE_AUTO_UPDATE_ENABLED = "BYTE_CODE_AUTO_UPDATE_ENABLED";

  public static ByteCodeViewerManager getInstance(Project project) {
    return ServiceManager.getService(project, ByteCodeViewerManager.class);
  }

  public ByteCodeViewerManager(Project project) {
    super(project);
  }

  @Override
  public String getShowInToolWindowProperty() {
    return SHOW_BYTECODE_IN_TOOL_WINDOW;
  }

  @Override
  public String getAutoUpdateEnabledProperty() {
    return BYTECODE_AUTO_UPDATE_ENABLED;
  }

  @Override
  protected String getToolwindowId() {
    return TOOLWINDOW_ID;
  }

  @Override
  protected String getAutoUpdateTitle() {
    return "Auto Show Bytecode for Selected Element";
  }

  @Override
  protected String getAutoUpdateDescription() {
    return "Show bytecode for current element automatically";
  }

  @Override
  protected String getRestorePopupDescription() {
    return "Restore bytecode popup behavior";
  }

  @Override
  protected ByteCodeViewerComponent createComponent() {
    return new ByteCodeViewerComponent(myProject);
  }

  @Override
  @Nullable
  protected String getTitle(PsiElement element) {
    PsiClass aClass = getContainingClass(element);
    if (aClass == null) return null;
    return SymbolPresentationUtil.getSymbolPresentableText(aClass);
  }

  private void updateByteCode(PsiElement element, ByteCodeViewerComponent component, Content content) {
    updateByteCode(element, component, content, getByteCode(element));
  }

  private void updateByteCode(PsiElement element, ByteCodeViewerComponent component, Content content, String byteCode) {
    if (!StringUtil.isEmpty(byteCode)) {
      component.setText(byteCode, element);
    }
    else {
      PsiElement presentableElement = getContainingClass(element);
      if (presentableElement == null) {
        presentableElement = element.getContainingFile();
        if (presentableElement == null && element instanceof PsiNamedElement) {
          presentableElement = element;
        }
        if (presentableElement == null) {
          component.setText("No bytecode found");
          return;
        }
      }
      component.setText("No bytecode found for " + SymbolPresentationUtil.getSymbolPresentableText(presentableElement));
    }
    content.setDisplayName(getTitle(element));
  }

  @Override
  protected void doUpdateComponent(PsiElement element, PsiElement originalElement, ByteCodeViewerComponent component) {
    final Content content = myToolWindow.getContentManager().getSelectedContent();
    if (content != null && element != null) {
      updateByteCode(element, component, content);
    }
  }

  @Override
  protected void doUpdateComponent(Editor editor, PsiFile psiFile) {
    final Content content = myToolWindow.getContentManager().getSelectedContent();
    if (content != null) {
      final ByteCodeViewerComponent component = (ByteCodeViewerComponent)content.getComponent();
      PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
      if (element != null) {
        updateByteCode(element, component, content);
      }
    }
  }

  @Override
  protected void doUpdateComponent(@NotNull PsiElement element) {
    doUpdateComponent(element, getByteCode(element));
  }

  protected void doUpdateComponent(@NotNull PsiElement element, final String newText) {
    final Content content = myToolWindow.getContentManager().getSelectedContent();
    if (content != null) {
      updateByteCode(element, (ByteCodeViewerComponent)content.getComponent(), content, newText);
    }
  }

  @Nullable
  public static String getByteCode(@NotNull PsiElement psiElement) {
    PsiClass containingClass = getContainingClass(psiElement);
    if (containingClass != null) {
      try {
        byte[] bytes = loadClassFileBytes(containingClass);
        if (bytes != null) {
          StringWriter writer = new StringWriter();
          try (PrintWriter printWriter = new PrintWriter(writer)) {
            new ClassReader(bytes).accept(new TraceClassVisitor(null, new Textifier(), printWriter), 0);
          }
          return writer.toString();
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return null;
  }

  private static byte[] loadClassFileBytes(PsiClass aClass) throws IOException {
    String jvmClassName = getJVMClassName(aClass);
    if (jvmClassName != null) {
      PsiClass fileClass = aClass;
      while (PsiUtil.isLocalOrAnonymousClass(fileClass)) {
        PsiClass containingClass = PsiTreeUtil.getParentOfType(fileClass, PsiClass.class);
        if (containingClass != null) {
          fileClass = containingClass;
        }
      }
      VirtualFile file = fileClass.getOriginalElement().getContainingFile().getVirtualFile();
      if (file != null) {
        ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(aClass.getProject());
        if (FileTypeRegistry.getInstance().isFileOfType(file, StdFileTypes.CLASS)) {
          // compiled class; looking for the right .class file (inner class 'A.B' is "contained" in 'A.class', but we need 'A$B.class')
          String classFileName = StringUtil.getShortName(jvmClassName) + ".class";
          if (index.isInLibraryClasses(file)) {
            VirtualFile classFile = file.getParent().findChild(classFileName);
            if (classFile != null) {
              return classFile.contentsToByteArray(false);
            }
          }
          else {
            File classFile = new File(file.getParent().getPath(), classFileName);
            if (classFile.isFile()) {
              return FileUtil.loadFileBytes(classFile);
            }
          }
        }
        else {
          // source code; looking for a .class file in compiler output
          Module module = index.getModuleForFile(file);
          if (module != null) {
            CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
            if (extension != null) {
              boolean inTests = index.isInTestSourceContent(file);
              VirtualFile classRoot = inTests ? extension.getCompilerOutputPathForTests() : extension.getCompilerOutputPath();
              if (classRoot != null) {
                String relativePath = jvmClassName.replace('.', '/') + ".class";
                File classFile = new File(classRoot.getPath(), relativePath);
                if (classFile.exists()) {
                  return FileUtil.loadFileBytes(classFile);
                }
              }
            }
          }
        }
      }
    }

    return null;
  }

  private static String getJVMClassName(PsiClass aClass) {
    if (!(aClass instanceof PsiAnonymousClass)) {
      return ClassUtil.getJVMClassName(aClass);
    }

    PsiClass containingClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
    if (containingClass != null) {
      return getJVMClassName(containingClass) + JavaAnonymousClassesHelper.getName((PsiAnonymousClass)aClass);
    }

    return null;
  }

  @Nullable
  public static PsiClass getContainingClass(@NotNull PsiElement psiElement) {
    for (ClassSearcher searcher : CLASS_SEARCHER_EP.getExtensions()) {
      PsiClass aClass = searcher.findClass(psiElement);
      if (aClass != null) {
        return aClass;
      }
    }

    PsiClass containingClass = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class, false);
    while (containingClass instanceof PsiTypeParameter) {
      containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class);
    }

    if (containingClass == null) {
      PsiFile containingFile = psiElement.getContainingFile();
      if (containingFile instanceof PsiClassOwner) {
        PsiClass[] classes = ((PsiClassOwner)containingFile).getClasses();
        if (classes.length == 1) return classes[0];

        TextRange textRange = psiElement.getTextRange();
        if (textRange != null) {
          for (PsiClass aClass : classes) {
            PsiElement navigationElement = aClass.getNavigationElement();
            TextRange classRange = navigationElement != null ? navigationElement.getTextRange() : null;
            if (classRange != null && classRange.contains(textRange)) return aClass;
          }
        }
      }
      return null;
    }

    return containingClass;
  }
}