/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android;

import com.android.sdklib.SdkConstants;
import com.intellij.codeInsight.javadoc.JavaDocExternalFilter;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.lang.documentation.CompositeDocumentationProvider;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDocumentationProvider implements DocumentationProvider, ExternalDocumentationProvider {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.AndroidDocumentationProvider");

  @Override
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    return null;
  }

  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    return null;
  }

  @Override
  public String generateDoc(PsiElement element, PsiElement originalElement) {
    return null;
  }

  @Override
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    return null;
  }

  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    return null;
  }

  @Override
  public String fetchExternalDocumentation(Project project, PsiElement element, List<String> docUrls) {
    return isMyContext(element, project) ?
           JavaDocumentationProvider.fetchExternalJavadoc(element, docUrls, new MyDocExternalFilter(project)) :
           null;
  }

  @Override
  public boolean hasDocumentationFor(PsiElement element, PsiElement originalElement) {
    return false;
  }

  private static boolean isMyContext(@NotNull final PsiElement element, @NotNull final Project project) {
    if (element instanceof PsiClass) {
      return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          PsiFile file = element.getContainingFile();
          if (file == null) {
            return false;
          }
          VirtualFile vFile = file.getVirtualFile();
          if (vFile == null) {
            return false;
          }
          String path = FileUtil.toSystemIndependentName(vFile.getPath());
          if (path.toLowerCase().indexOf("/" + SdkConstants.FN_FRAMEWORK_LIBRARY + "!/") >= 0) {
            if (ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).size() > 0) {
              VirtualFile jarFile = JarFileSystem.getInstance().getVirtualFileForJar(vFile);
              return jarFile != null && SdkConstants.FN_FRAMEWORK_LIBRARY.equals(jarFile.getName());
            }
          }
          return false;
        }
      });
    }
    return false;
  }

  private static class MyDocExternalFilter extends JavaDocExternalFilter {
    public MyDocExternalFilter(Project project) {
      super(project);
    }

    @Override
    protected void doBuildFromStream(String surl, Reader input, StringBuffer data) throws IOException {
      try {
        if (ourAnchorsuffix.matcher(surl).find()) {
          super.doBuildFromStream(surl, input, data);
          return;
        }
        final BufferedReader buf = new BufferedReader(input);

        @NonNls String startSection = "<!-- ======== START OF CLASS DATA ======== -->";
        @NonNls String endHeader = "<!-- END HEADER -->";

        data.append(HTML);

        String read;

        do {
          read = buf.readLine();
        }
        while (read != null && read.toUpperCase().indexOf(startSection) == -1);

        if (read == null) {
          data.delete(0, data.length());
          return;
        }

        data.append(read).append("\n");

        boolean skip = false;
        while (((read = buf.readLine()) != null) && read.toLowerCase().indexOf("class overview") < 0) {
          if (!skip && read.length() > 0) {
            data.append(read).append("\n");
          }
          if (read.toUpperCase().indexOf(endHeader) != -1) {
            skip = true;
          }
        }

        if (read != null) {
          data.append("<br><div>\n");
          while (((read = buf.readLine()) != null) && !read.toUpperCase().startsWith("<H2>")) {
            data.append(read).append("\n");
          }
          data.append("</div>\n");
        }
        data.append(HTML_CLOSE);
      }
      catch (Exception e) {
        LOG.error(e.getMessage(), e, "URL: " + surl);
      }
    }
  }
}
