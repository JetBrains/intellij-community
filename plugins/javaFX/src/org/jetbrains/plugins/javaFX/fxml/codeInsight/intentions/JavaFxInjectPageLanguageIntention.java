// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.fxml.codeInsight.intentions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlProcessingInstruction;
import com.intellij.psi.xml.XmlProlog;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.JavaFXBundle;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class JavaFxInjectPageLanguageIntention extends PsiElementBaseIntentionAction {
  public static final Logger LOG = Logger.getInstance(JavaFxInjectPageLanguageIntention.class);

  public static Set<String> getAvailableLanguages(Project project) {
    final List<ScriptEngineFactory> engineFactories = new ScriptEngineManager(composeUserClassLoader(project)).getEngineFactories();

    if (engineFactories != null) {
      final Set<String> availableNames = new TreeSet<>();
      for (ScriptEngineFactory factory : engineFactories) {
        final String engineName = (String)factory.getParameter(ScriptEngine.NAME);
        availableNames.add(engineName);
      }
      return availableNames;
    }

    return null;
  }

  private static ClassLoader composeUserClassLoader(Project project) {
    final List<URL> urls = new ArrayList<>();
    final List<String> list = OrderEnumerator.orderEntries(project).recursively().librariesOnly().runtimeOnly().getPathsList().getPathList();
    for (String path : list) {
      try {
        urls.add(new File(FileUtil.toSystemIndependentName(path)).toURI().toURL());
      }
      catch (MalformedURLException e1) {
        LOG.info(e1);
      }
    }
    return new URLClassLoader(urls.toArray(new URL[0]));
  }

  @Override
  public void invoke(@NotNull final Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(element)) return;
    final XmlFile containingFile = (XmlFile)element.getContainingFile();
    final Set<String> availableLanguages = getAvailableLanguages(project);

    LOG.assertTrue(availableLanguages != null);
    final List<String> list = new ArrayList<>(availableLanguages);

    if (availableLanguages.size() == 1) {
      registerPageLanguage(project, containingFile, availableLanguages.iterator().next());
    } else {
      JBPopupFactory.getInstance()
        .createPopupChooserBuilder(list)
        .setItemChosenCallback(
          (selectedValue) -> registerPageLanguage(project, containingFile, selectedValue))
        .createPopup().showInBestPositionFor(editor);
    }
  }

  public void registerPageLanguage(final Project project, final XmlFile containingFile, final String languageName) {
    WriteCommandAction.writeCommandAction(project).withName(getFamilyName()).run(() -> {
      final PsiFileFactory factory = PsiFileFactory.getInstance(project);
      final XmlFile dummyFile = (XmlFile)factory.createFileFromText("_Dummy_.fxml", XmlFileType.INSTANCE,
                                                                    "<?language " + languageName + "?>");
      final XmlDocument document = dummyFile.getDocument();
      if (document != null) {
        final XmlProlog prolog = document.getProlog();
        final Collection<XmlProcessingInstruction> instructions = PsiTreeUtil.findChildrenOfType(prolog, XmlProcessingInstruction.class);
        LOG.assertTrue(instructions.size() == 1);
        final XmlDocument xmlDocument = containingFile.getDocument();
        if (xmlDocument != null) {
          final XmlProlog xmlProlog = xmlDocument.getProlog();
          if (xmlProlog != null) {
            final PsiElement element = xmlProlog.addBefore(instructions.iterator().next(), xmlProlog.getFirstChild());
            xmlProlog.addAfter(PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n\n"), element);
          }
        }
      }
    });
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (ContainerUtil.isEmpty(getAvailableLanguages(project))) {
      return false;
    }
    setText(getFamilyName());
    return element.isValid();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaFXBundle.message("javafx.inject.page.language.intention.family.name");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
