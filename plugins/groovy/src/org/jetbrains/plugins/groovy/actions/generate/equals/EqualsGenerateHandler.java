package org.jetbrains.plugins.groovy.actions.generate.equals;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.generation.*;
import com.intellij.codeInsight.generation.ui.GenerateEqualsWizard;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.actions.generate.GroovyCodeInsightBundle;

import java.util.Collection;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 28.05.2008
 */
public class EqualsGenerateHandler extends GenerateMembersHandlerBase {

  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.actions.generate.equals.EqualsGenerateHandler");
  private PsiField[] myEqualsFields = null;
  private PsiField[] myHashCodeFields = null;
  private PsiField[] myNonNullFields = null;
  private static final PsiElementClassMember[] DUMMY_RESULT = new PsiElementClassMember[1];
  private Collection<PsiMethod> myMethods;
  private PsiClass myClass;

  public EqualsGenerateHandler() {
    super("");
  }


  @Nullable
  protected ClassMember[] chooseOriginalMembers(PsiClass aClass, Project project) {
    myEqualsFields = null;
    myHashCodeFields = null;
    myNonNullFields = PsiField.EMPTY_ARRAY;

    myClass = aClass;
    GlobalSearchScope scope = aClass.getResolveScope();
    final PsiMethod equalsMethod = GrGenerateEqualsHelper.findMethod(aClass, GrGenerateEqualsHelper.getEqualsSignature(project, scope));
    final PsiMethod hashCodeMethod = GrGenerateEqualsHelper.findMethod(aClass, GrGenerateEqualsHelper.getHashCodeSignature());

    boolean needEquals = equalsMethod == null;
    boolean needHashCode = hashCodeMethod == null;
    if (!needEquals && !needHashCode) {
      String text = aClass instanceof PsiAnonymousClass
          ? GroovyCodeInsightBundle.message("generate.equals.and.hashcode.already.defined.warning.anonymous")
          : GroovyCodeInsightBundle.message("generate.equals.and.hashcode.already.defined.warning", aClass.getQualifiedName());

      if (Messages.showYesNoDialog(project, text,
          GroovyCodeInsightBundle.message("generate.equals.and.hashcode.already.defined.title"),
          Messages.getQuestionIcon()) == DialogWrapper.OK_EXIT_CODE) {
        if (!ApplicationManager.getApplication().runWriteAction(new Computable<Boolean>() {
          public Boolean compute() {
            try {
              equalsMethod.delete();
              hashCodeMethod.delete();
              return Boolean.TRUE;
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
              return Boolean.FALSE;
            }
          }
        }).booleanValue()) {
          return null;
        } else {
          needEquals = needHashCode = true;
        }
      } else {
        return null;
      }
    }

    GenerateEqualsWizard wizard = new GenerateEqualsWizard(project, aClass, needEquals, needHashCode);
    wizard.show();
    if (!wizard.isOK()) return null;
    myEqualsFields = wizard.getEqualsFields();
    myHashCodeFields = wizard.getHashCodeFields();
    myNonNullFields = wizard.getNonNullFields();
    return DUMMY_RESULT;
  }

  @NotNull
  protected List<? extends GenerationInfo> generateMemberPrototypes(PsiClass aClass, ClassMember[] originalMembers) throws IncorrectOperationException {
//     try {
    Project project = aClass.getProject();
    final boolean useInstanceofToCheckParameterType = CodeInsightSettings.getInstance().USE_INSTANCEOF_ON_EQUALS_PARAMETER;

    GrGenerateEqualsHelper helper = new GrGenerateEqualsHelper(project, aClass, myEqualsFields, myHashCodeFields, myNonNullFields, useInstanceofToCheckParameterType);
    myMethods = helper.generateMembers();
    return OverrideImplementUtil.convert2GenerationInfos(myMethods);
//    }

//    catch (GrGenerateEqualsHelper .NoObjectClassException e) {
//      ApplicationManager.getApplication().invokeLater(new Runnable() {
//          public void run() {
//            Messages.showErrorDialog(GroovyCodeInsightBundle
//                .message("generate.equals.and.hashcode.error.no.object.class.message"),
//                GroovyCodeInsightBundle.message("generate.equals.and.hashcode.error.no.object.class.title"));
//          }
//        });
//      return Collections.emptyList();
//    }
  }

  protected ClassMember[] getAllOriginalMembers(PsiClass aClass) {
    return new ClassMember[0];
  }

  protected GenerationInfo[] generateMemberPrototypes(PsiClass aClass, ClassMember originalMember) throws IncorrectOperationException {
    return new GenerationInfo[0];
  }

  protected void cleanup() {
    super.cleanup();

//    PsiUtil.reformatCode(myClass);
//    for (PsiMethod method : myMethods) {
//      PsiUtil.reformatCode(method);
//      break;
//    }

    myEqualsFields = null;
    myHashCodeFields = null;
    myNonNullFields = null;
  }

  public boolean startInWriteAction() {
      return true;
    } 
}
