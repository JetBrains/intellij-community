// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.codeInsight;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxClassTagDescriptorBase;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxPropertyTagDescriptor;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxRootTagDescriptor;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxStaticSetterAttributeDescriptor;

import java.util.*;

public final class JavaFxImportsOptimizer implements ImportOptimizer {
  @Override
  public boolean supports(@NotNull PsiFile file) {
    return JavaFxFileTypeFactory.isFxml(file);
  }

  @Override
  public @NotNull Runnable processFile(final @NotNull PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    if (vFile instanceof VirtualFileWindow) vFile = ((VirtualFileWindow)vFile).getDelegate();
    final Project project = file.getProject();
    if (vFile == null || !ProjectRootManager.getInstance(project).getFileIndex().isInSourceContent(vFile)) {
      return EmptyRunnable.INSTANCE;
    }
    final @NotNull List<ImportHelper.Import> names = new ArrayList<>();
    final Set<String> demandedForNested = new HashSet<>();
    collectNamesToImport(names, demandedForNested, (XmlFile)file);
    names.sort((o1, o2) -> StringUtil.compare(o1.name(), o2.name(), true));
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(file);
    final @NotNull List<ImportHelper.Import> sortedNames = ImportHelper.sortItemsAccordingToSettings(names, settings);
    final Map<String, Boolean> onDemand = new HashMap<>();
    ImportHelper.collectOnDemandImports(sortedNames, settings, onDemand);
    for (String s : demandedForNested) {
      onDemand.put(s, false);
    }
    final Set<String> imported = new HashSet<>();
    final List<String> imports = new ArrayList<>();
    for (ImportHelper.Import anImport : sortedNames) {
      final String qName = anImport.name();
      final String packageName = StringUtil.getPackageName(qName);
      if (imported.contains(packageName) || imported.contains(qName)) {
        continue;
      }
      if (onDemand.containsKey(packageName)) {
        imported.add(packageName);
        imports.add("<?import " + packageName + ".*?>");
      } else {
        imported.add(qName);
        imports.add("<?import " + qName + "?>");
      }
    }
    final PsiFileFactory factory = PsiFileFactory.getInstance(file.getProject());

    final XmlFile dummyFile = (XmlFile)factory.createFileFromText("_Dummy_.fxml", XmlFileType.INSTANCE, StringUtil.join(imports, "\n"));
    final XmlDocument document = dummyFile.getDocument();
    final XmlProlog newImportList = document != null ? document.getProlog() : null;
    if (newImportList == null) return EmptyRunnable.getInstance();
    return () -> {
      final XmlDocument xmlDocument = ((XmlFile)file).getDocument();
      final XmlProlog prolog = xmlDocument != null ? xmlDocument.getProlog() : null;
      if (prolog != null) {
        final Collection<XmlProcessingInstruction> instructions = PsiTreeUtil.findChildrenOfType(prolog, XmlProcessingInstruction.class);
        for (final XmlProcessingInstruction instruction : instructions) {
          final ASTNode node = instruction.getNode();
          final ASTNode nameNode = node.findChildByType(XmlTokenType.XML_NAME);
          if (nameNode != null && nameNode.getText().equals("import")) {
            instruction.delete();
          }
        }
        prolog.add(newImportList);
      } else {
        document.addBefore(newImportList, document.getRootTag());
      }
    };
  }

  private static void collectNamesToImport(final @NotNull List<ImportHelper.Import> names,
                                           final @NotNull Collection<String> demandedForNested,
                                           @NotNull XmlFile file) {
    file.accept(new JavaFxUsedClassesVisitor() {
      @Override
      protected void appendClassName(String fqn) {
        names.add(new ImportHelper.Import(fqn, false));
      }

      @Override
      protected void appendDemandedPackageName(@NotNull String packageName) {
        demandedForNested.add(packageName);
      }
    });
  }

  public abstract static class JavaFxUsedClassesVisitor extends XmlRecursiveElementVisitor {
    @Override
    public void visitXmlProlog(@NotNull XmlProlog prolog) {}

    @Override
    public void visitXmlProcessingInstruction(@NotNull XmlProcessingInstruction processingInstruction) {}

    @Override
    public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
      final XmlAttributeDescriptor descriptor = attribute.getDescriptor();
      if (descriptor instanceof JavaFxStaticSetterAttributeDescriptor) {
        final PsiElement declaration = descriptor.getDeclaration();
        if (declaration instanceof PsiMember) {
          appendClassName(((PsiMember)declaration).getContainingClass());
        }
      }
      else if (descriptor instanceof JavaFxRootTagDescriptor.RootTagTypeAttributeDescriptor) {
        appendClassName(JavaFxPsiUtil.findPsiClass(attribute.getValue(), attribute));
      }
    }

    @Override
    public void visitXmlTag(@NotNull XmlTag tag) {
      super.visitXmlTag(tag);
      final XmlElementDescriptor descriptor = tag.getDescriptor();
      if (descriptor instanceof JavaFxClassTagDescriptorBase) {
        appendClassName(descriptor.getDeclaration());
      }
      else if (descriptor instanceof JavaFxPropertyTagDescriptor && ((JavaFxPropertyTagDescriptor)descriptor).isStatic()) {
        final PsiElement declaration = descriptor.getDeclaration();
        if (declaration instanceof PsiMember) {
          appendClassName(((PsiMember)declaration).getContainingClass());
        }
      }
    }

    private void appendClassName(PsiElement declaration) {
      if (declaration instanceof PsiClass psiClass) {
        final String ownerClassQN = getTopmostOwnerClassQualifiedName(psiClass);
        if (ownerClassQN != null) {
          appendClassName(ownerClassQN);
          final String ownerClassPackageName = StringUtil.getPackageName(ownerClassQN);
          if (!StringUtil.isEmpty(ownerClassPackageName)) {
            appendDemandedPackageName(ownerClassPackageName);
          }
        }
        else {
          final String classQN = psiClass.getQualifiedName();
          if (classQN != null) {
            appendClassName(classQN);
          }
        }
      }
    }

    private static @Nullable String getTopmostOwnerClassQualifiedName(@NotNull PsiClass psiClass) {
      PsiClass ownerClass = null;
      for (PsiClass aClass = psiClass.getContainingClass(); aClass != null; aClass = aClass.getContainingClass()) {
        ownerClass = aClass;
      }
      if (ownerClass != null) {
        return ownerClass.getQualifiedName();
      }
      return null;
    }

    protected abstract void appendClassName(String fqn);

    protected abstract void appendDemandedPackageName(@NotNull String packageName);
  }
}
