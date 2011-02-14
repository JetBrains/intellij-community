package com.intellij.coverage;

import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.javaee.run.configuration.CommonModel;
import com.intellij.javaee.web.WebUtil;
import com.intellij.lang.jsp.JspFileViewProvider;
import com.intellij.lang.jsp.JspxFileViewProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.WebDirectoryElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * User: anna
 * Date: 2/12/11
 */
public class DefaultJ2EEContainerExtension extends JavaCoverageEngineExtension {

  @Override
  public boolean isApplicableTo(@Nullable RunConfigurationBase conf) {
    return conf instanceof CommonModel && ((CommonModel)conf).isLocal();
  }

  @Override
  public boolean suggestQualifiedName(@NotNull final PsiFile sourceFile, final PsiClass[] classes, final Set<String> names) {
    final FileViewProvider viewProvider = sourceFile.getViewProvider();
    if (!(viewProvider instanceof JspFileViewProvider || viewProvider instanceof JspxFileViewProvider)) {
      return false;
    }
    final VirtualFile virtualFile = sourceFile.getVirtualFile();
    if (virtualFile != null) {
      final WebDirectoryElement webDirectoryElement = WebUtil.findWebDirectoryByFile(virtualFile, sourceFile.getProject());
      if (webDirectoryElement != null) {
        final WebDirectoryElement parentDirectory = webDirectoryElement.getParentDirectory();
        String webRoot = parentDirectory != null ? parentDirectory.getPath() : "/";
        if (!webRoot.endsWith("/")) {
          webRoot += "/";
        }
        names.add("org.apache.jsp" + makeJavaIdentifier(webRoot + virtualFile.getName(), sourceFile.getProject()));
        return true;
      }
    }
    return false;
  }

  /**
   * Converts the given identifier to a legal Java identifier
   * {@see org.apache.jasper.compiler.JspUtil}
   */
  public static String makeJavaIdentifier(String identifier, Project project) {
    StringBuffer modifiedIdentifier =
      new StringBuffer(identifier.length());
    if (!Character.isJavaIdentifierStart(identifier.charAt(0)) && identifier.charAt(0) != '/') {
      modifiedIdentifier.append('_');
    }
    for (int i = 0; i < identifier.length(); i++) {
      char ch = identifier.charAt(i);
      if (ch == '/') {
        modifiedIdentifier.append('.');
      }
      else if (Character.isJavaIdentifierPart(ch) && ch != '_') {
        modifiedIdentifier.append(ch);
      } else if (ch == '.') {
        modifiedIdentifier.append('_');
      }
      else {
        modifiedIdentifier.append(mangleChar(ch));
      }
    }
    if (JavaPsiFacade.getInstance(project).getNameHelper().isKeyword(modifiedIdentifier.toString())) {
      modifiedIdentifier.append('_');
    }
    return modifiedIdentifier.toString();
  }

  /**
   * Mangle the specified character to create a legal Java class name.
   */
  public static String mangleChar(char ch) {
    char[] result = new char[5];
    result[0] = '_';
    result[1] = Character.forDigit((ch >> 12) & 0xf, 16);
    result[2] = Character.forDigit((ch >> 8) & 0xf, 16);
    result[3] = Character.forDigit((ch >> 4) & 0xf, 16);
    result[4] = Character.forDigit(ch & 0xf, 16);
    return new String(result);
  }
}
