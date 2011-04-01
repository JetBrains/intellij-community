/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class ConvertToJavaProcessor extends BaseRefactoringProcessor {
  private static Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.convertToJava.ConvertToJavaProcessor");
  private GroovyFile[] myFiles;

  protected ConvertToJavaProcessor(Project project, GroovyFile[] files) {
    super(project);
    myFiles = files;
  }

  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new UsageViewDescriptorAdapter() {
      @NotNull
      @Override
      public PsiElement[] getElements() {
        return myFiles;
      }

      @Override
      public String getProcessedElementsHeader() {
        return GroovyRefactoringBundle.message("files.to.be.converted");
      }
    };
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    return new UsageInfo[0];
  }

  //private static String
  @Override
  protected void performRefactoring(UsageInfo[] usages) {
    final GroovyToJavaGenerator generator = new GroovyToJavaGenerator(myProject, Collections.<VirtualFile>emptyList(), true);
    final PsiFileFactory fileFactory = PsiFileFactory.getInstance(myProject);

    PsiFile fileToOpen = null;
    for (GroovyFile file : myFiles) {
      final PsiDirectory dir = file.getContainingDirectory();
      LOG.assertTrue(dir != null);
      final Map<String, String> fileMap = generator.generateStubs(file);
      for (String fileName : fileMap.keySet()) {

        file.delete();

        final PsiFile[] files = dir.getFiles();
        Set<String> fileNames = new HashSet<String>();
        for (PsiFile psiFile : files) {
          fileNames.add(psiFile.getName());
        }
        int index = 0;
        String prefix = FileUtil.getNameWithoutExtension(fileName);
        while (fileNames.contains(fileName)) {
          fileName = prefix + index + ".java";
        }

        final PsiFile newFile = fileFactory.createFileFromText(fileName, fileMap.get(fileName));
        dir.add(newFile);
      }
    }


  }

  @Override
  protected String getCommandName() {
    return GroovyRefactoringBundle.message("converting.files.to.java");
  }
}
