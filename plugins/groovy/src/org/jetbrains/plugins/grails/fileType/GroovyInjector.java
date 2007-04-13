package org.jetbrains.plugins.grails.fileType;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

public class GroovyInjector implements ProjectComponent, LanguageInjector
{
  private Project project;

  public GroovyInjector(Project project)
  {
    this.project = project;
  }

  public void initComponent()
  {
    PsiManager.getInstance(project).registerLanguageInjector(new LanguageInjector()
    {
      public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces injectionPlacesRegistrar)
      {
        if (!(host.getLanguage() instanceof XMLLanguage))
          return;

        VirtualFile virtualFile = PsiUtil.getVirtualFile(host);
        if (virtualFile == null || !virtualFile.getName().toLowerCase().endsWith(".gsp"))
          return;

        String value = null;
        if (host instanceof XmlAttributeValue)
        {
          XmlAttributeValue attr = (XmlAttributeValue) host;
          value = attr.getValue();
        }
//        else
//        if (host instanceof XmlText)
//        {
//          XmlText xmlText = (XmlText) host;
//          value = xmlText.getText();
//        }

        if (value != null)
        {
          int start = value.indexOf("${");
          if (start == -1)
            return;

          int end = value.indexOf("}", start + 2);
          if (end == -1)
            return;

          injectionPlacesRegistrar.addPlace(GroovyFileType.GROOVY_FILE_TYPE.getLanguage(), new TextRange(start + 2, end), "", ";");
        }
      }
    });
  }

  public void disposeComponent()
  {
  }

  @NotNull
  public String getComponentName()
  {
    return "GroovyInjector";
  }

  public void projectOpened()
  {
    // called when project is opened
  }

  public void projectClosed()
  {
    // called when project is being closed
  }

  public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces injectionPlacesRegistrar)
  {
  }
}
