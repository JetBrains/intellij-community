package org.jetbrains.android;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.safeDelete.JavaSafeDeleteProcessor;
import com.intellij.refactoring.safeDelete.NonCodeUsageSearchInfo;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegateBase;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.manifest.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidComponentSafeDeleteProcessor extends SafeDeleteProcessorDelegateBase {

  private final JavaSafeDeleteProcessor myBaseHandler = new JavaSafeDeleteProcessor();

  private JavaSafeDeleteProcessor getBaseHandler() {
    return myBaseHandler;
  }

  @Override
  public boolean handlesElement(PsiElement element) {
    return getBaseHandler().handlesElement(element) &&
           element instanceof PsiClass &&
           AndroidFacet.getInstance(element) != null &&
           isAndroidComponent((PsiClass)element);
  }

  private static boolean isAndroidComponent(@NotNull PsiClass c) {
    final String[] componentClasses =
      {AndroidUtils.ACTIVITY_BASE_CLASS_NAME, AndroidUtils.SERVICE_CLASS_NAME, AndroidUtils.RECEIVER_CLASS_NAME,
        AndroidUtils.PROVIDER_CLASS_NAME};

    final Project project = c.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);

    for (String componentClassName : componentClasses) {
      final PsiClass componentClass = facade.findClass(componentClassName, ProjectScope.getAllScope(project));
      if (componentClass != null && c.isInheritor(componentClass, true)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public NonCodeUsageSearchInfo findUsages(PsiElement element, PsiElement[] allElementsToDelete, List<UsageInfo> result) {
    final ArrayList<UsageInfo> usages = new ArrayList<UsageInfo>();
    final NonCodeUsageSearchInfo info = getBaseHandler().findUsages(element, allElementsToDelete, usages);
    if (info == null) {
      return info;
    }

    assert element instanceof PsiClass;
    final PsiClass componentClass = (PsiClass)element;
    final AndroidAttributeValue<PsiClass> declaration = findComponentDeclarationInManifest(componentClass);
    if (declaration == null) {
      return info;
    }

    final XmlAttributeValue declarationAttributeValue = declaration.getXmlAttributeValue();

    for (UsageInfo usage : usages) {
      if (declarationAttributeValue != usage.getElement()) {
        result.add(usage);
      }
    }
    return info;
  }

  @Override
  public Collection<? extends PsiElement> getElementsToSearch(PsiElement element,
                                                              @Nullable Module module,
                                                              Collection<PsiElement> allElementsToDelete) {
    return getBaseHandler().getElementsToSearch(element, module, allElementsToDelete);
  }

  @Override
  public Collection<PsiElement> getAdditionalElementsToDelete(PsiElement element,
                                                              Collection<PsiElement> allElementsToDelete,
                                                              boolean askUser) {
    return getBaseHandler().getAdditionalElementsToDelete(element, allElementsToDelete, askUser);
  }

  @Override
  public Collection<String> findConflicts(PsiElement element, PsiElement[] allElementsToDelete) {
    return getBaseHandler().findConflicts(element, allElementsToDelete);
  }

  @Override
  public UsageInfo[] preprocessUsages(Project project, UsageInfo[] usages) {
    return usages;
  }

  @Override
  public void prepareForDeletion(PsiElement element) throws IncorrectOperationException {
    assert element instanceof PsiClass;
    final AndroidAttributeValue<PsiClass> declaration = findComponentDeclarationInManifest((PsiClass)element);
    if (declaration != null) {
      final XmlAttribute declarationAttr = declaration.getXmlAttribute();
      if (declarationAttr != null) {
        final XmlTag declarationTag = declarationAttr.getParent();
        if (declarationTag != null) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              declarationTag.delete();
            }
          });
        }
      }
    }
    getBaseHandler().prepareForDeletion(element);
  }

  @Nullable
  private static AndroidAttributeValue<PsiClass> findComponentDeclarationInManifest(@NotNull PsiClass aClass) {
    final AndroidFacet facet = AndroidFacet.getInstance(aClass);
    if (facet == null) {
      return null;
    }

    final Manifest manifest = facet.getManifest();
    if (manifest == null) {
      return null;
    }

    final Application application = manifest.getApplication();
    if (application == null) {
      return null;
    }

    if (isInheritor(aClass, AndroidUtils.ACTIVITY_BASE_CLASS_NAME)) {
      for (Activity activity : application.getActivities()) {
        final AndroidAttributeValue<PsiClass> activityClass = activity.getActivityClass();
        if (activityClass.getValue() == aClass) {
          return activityClass;
        }
      }
    }
    else if (isInheritor(aClass, AndroidUtils.SERVICE_CLASS_NAME)) {
      for (Service service : application.getServices()) {
        final AndroidAttributeValue<PsiClass> serviceClass = service.getServiceClass();
        if (serviceClass.getValue() == aClass) {
          return serviceClass;
        }
      }
    }
    else if (isInheritor(aClass, AndroidUtils.RECEIVER_CLASS_NAME)) {
      for (Receiver receiver : application.getReceivers()) {
        final AndroidAttributeValue<PsiClass> receiverClass = receiver.getReceiverClass();
        if (receiverClass.getValue() == aClass) {
          return receiverClass;
        }
      }
    }
    else if (isInheritor(aClass, AndroidUtils.PROVIDER_CLASS_NAME)) {
      for (Provider provider : application.getProviders()) {
        final AndroidAttributeValue<PsiClass> providerClass = provider.getProviderClass();
        if (providerClass.getValue() == aClass) {
          return providerClass;
        }
      }
    }
    return null;
  }

  private static boolean isInheritor(@NotNull PsiClass aClass, @NotNull String baseClassQName) {
    final Project project = aClass.getProject();
    final PsiClass baseClass = JavaPsiFacade.getInstance(project).findClass(baseClassQName, ProjectScope.getAllScope(project));
    return baseClass != null && aClass.isInheritor(baseClass, true);
  }

  @Override
  public boolean isToSearchInComments(PsiElement element) {
    return getBaseHandler().isToSearchInComments(element);
  }

  @Override
  public void setToSearchInComments(PsiElement element, boolean enabled) {
    getBaseHandler().setToSearchInComments(element, enabled);
  }

  @Override
  public boolean isToSearchForTextOccurrences(PsiElement element) {
    return getBaseHandler().isToSearchForTextOccurrences(element);
  }

  @Override
  public void setToSearchForTextOccurrences(PsiElement element, boolean enabled) {
    getBaseHandler().setToSearchForTextOccurrences(element, enabled);
  }
}
