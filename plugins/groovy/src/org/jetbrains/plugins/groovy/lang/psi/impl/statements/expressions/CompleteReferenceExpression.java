package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicPropertiesManager;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.*;

import java.util.*;

/**
 * @author ven
 */
public class CompleteReferenceExpression {
  public static Object[] getVariants(GrReferenceExpressionImpl refExpr) {
    Object[] propertyVariants = getVariantsImpl(refExpr, GrReferenceExpressionImpl.getMethodOrPropertyResolveProcessor(refExpr, null, true, false));
    PsiType type = null;
    final GrExpression qualifier = refExpr.getQualifierExpression();
    if (qualifier == null) {
      PsiElement parent = refExpr.getParent();
      if (parent instanceof GrArgumentList) {
        final PsiElement pparent = parent.getParent();
        if (pparent instanceof GrExpression) {
          GrExpression call = (GrExpression) pparent; //add named argument label variants
          type = call.getType();
        }
      }
    } else {
      type = qualifier.getType();
    }

    if (type instanceof PsiClassType) {
      PsiClass clazz = ((PsiClassType) type).resolve();
      if (clazz != null) {
        List<LookupElement> props = getPropertyVariants(refExpr, clazz);

        if (props.size() > 0) {
          propertyVariants = ArrayUtil.mergeArrays(propertyVariants, props.toArray(new Object[props.size()]), Object.class);
        }

        propertyVariants = ArrayUtil.mergeArrays(propertyVariants, clazz.getFields(), Object.class);
      }
    }

    //TODO: check it
    propertyVariants = getDynamicPropertiesVariants(refExpr, propertyVariants, type);

    if (refExpr.getKind() == GrReferenceExpressionImpl.Kind.TYPE_OR_PROPERTY) {
      ResolverProcessor classVariantsCollector = new ClassResolverProcessor(null, refExpr, true);
      final Object[] classVariants = getVariantsImpl(refExpr, classVariantsCollector);
      return ArrayUtil.mergeArrays(propertyVariants, classVariants, Object.class);
    } else {
      return propertyVariants;
    }

  }

  private static Object[] getDynamicPropertiesVariants(GrReferenceExpressionImpl refExpr, Object[] propertyVariants, PsiType type) {
    final PsiFile containingFile = refExpr.getContainingFile();
    final PsiFile originalFile = containingFile.getOriginalFile();
    if (originalFile == null) {
      return propertyVariants;
    }

    final VirtualFile virtualFile = originalFile.getVirtualFile();
    if (virtualFile == null) {
      return propertyVariants;
    }

    Module module = ProjectRootManager.getInstance(refExpr.getProject()).getFileIndex().getModuleForFile(virtualFile);
    if (module == null) {
      return propertyVariants;
    }
    String[] dynamicPropertiesOfClass;
    if (type == null) {
      return propertyVariants;
    }
    dynamicPropertiesOfClass = DynamicPropertiesManager.getInstance(refExpr.getProject()).findDynamicPropertiesOfClass(module.getName(), type.getCanonicalText());

//    if (type instanceof PsiPrimitiveType) {
//      type = ((PsiPrimitiveType) type).getBoxedType(refExpr);
//    }

    if (type instanceof PsiClassType) {
      final PsiClass psiClass = ((PsiClassType) type).resolve();

      if (psiClass == null) {
        return propertyVariants;
      }
      final Set<PsiClass> supers = GroovyUtils.findAllSupers(psiClass);

      for (PsiClass aSuper : supers) {
        dynamicPropertiesOfClass = DynamicPropertiesManager.getInstance(refExpr.getProject()).findDynamicPropertiesOfClass(module.getName(), aSuper.getQualifiedName());
        propertyVariants = ArrayUtil.mergeArrays(propertyVariants, dynamicPropertiesOfClass, Object.class);
      }

      if (dynamicPropertiesOfClass.length != 0) {
        propertyVariants = ArrayUtil.mergeArrays(propertyVariants, dynamicPropertiesOfClass, Object.class);
      }
    }
    return propertyVariants;
  }

  private static List<LookupElement> getPropertyVariants(GrReferenceExpression refExpr, PsiClass clazz) {
    List<LookupElement> props = new ArrayList<LookupElement>();
    final LookupElementFactory factory = LookupElementFactory.getInstance();
    final PsiClass eventListener = refExpr.getManager().findClass("java.util.EventListener", refExpr.getResolveScope());
    for (PsiMethod method : clazz.getAllMethods()) {
      if (PsiUtil.isSimplePropertySetter(method)) {
        String prop = PropertyUtil.getPropertyName(method);
        if (prop != null) {
          props.add(factory.createLookupElement(prop).setIcon(GroovyIcons.PROPERTY));
        }
      } else if (eventListener != null) {
        addListenerProperties(method, eventListener, props, factory);
      }
    }
    return props;
  }

  private static void addListenerProperties(PsiMethod method, PsiClass eventListenerClass,
                                            List<LookupElement> result, LookupElementFactory factory) {
    if (method.getName().startsWith("add") &&
        method.getParameterList().getParametersCount() == 1) {
      final PsiParameter parameter = method.getParameterList().getParameters()[0];
      final PsiType type = parameter.getType();
      if (type instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType) type;
        final PsiClass listenerClass = classType.resolve();
        if (listenerClass != null) {
          final PsiMethod[] listenerMethods = listenerClass.getMethods();
          if (InheritanceUtil.isInheritorOrSelf(listenerClass, eventListenerClass, true)) {
            for (PsiMethod listenerMethod : listenerMethods) {
              result.add(factory.createLookupElement(listenerMethod.getName()).setIcon(GroovyIcons.PROPERTY));
            }
          }
        }
      }
    }
  }


  private static Object[] getVariantsImpl(GrReferenceExpression refExpr, ResolverProcessor processor) {
    GrExpression qualifier = refExpr.getQualifierExpression();
    String[] sameQualifier = getVariantsWithSameQualifier(qualifier, refExpr);
    if (qualifier == null) {
      ResolveUtil.treeWalkUp(refExpr, processor);
      qualifier = PsiImplUtil.getRuntimeQualifier(refExpr);
      if (qualifier != null) {
        getVariantsFromQualifier(refExpr, processor, qualifier);
      }
    } else {
      if (refExpr.getDotTokenType() != GroovyTokenTypes.mSPREAD_DOT) {
        getVariantsFromQualifier(refExpr, processor, qualifier);
      } else {
        getVariantsFromQualifierForSpreadOperator(refExpr, processor, qualifier);
      }
    }

    GroovyResolveResult[] candidates = processor.getCandidates();
    if (candidates.length == 0 && sameQualifier.length == 0) return PsiNamedElement.EMPTY_ARRAY;
    PsiElement[] elements = ResolveUtil.mapToElements(candidates);
    LookupElement[] propertyLookupElements = addPretendedProperties(elements);
    Object[] variants = GroovyCompletionUtil.getCompletionVariants(candidates);
    variants = ArrayUtil.mergeArrays(variants, propertyLookupElements, Object.class);
    return ArrayUtil.mergeArrays(variants, sameQualifier, Object.class);
  }

  private static void getVariantsFromQualifierForSpreadOperator(GrReferenceExpression refExpr, ResolverProcessor processor, GrExpression qualifier) {
    PsiType qualifierType = qualifier.getType();
    if (qualifierType instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = ((PsiClassType) qualifierType).resolveGenerics();
      PsiClass clazz = result.getElement();
      if (clazz != null) {
        PsiClass listClass = refExpr.getManager().findClass("java.util.List", refExpr.getResolveScope());
        if (listClass != null && listClass.getTypeParameters().length == 1) {
          PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(listClass, clazz, result.getSubstitutor());
          if (substitutor != null) {
            PsiType componentType = substitutor.substitute(listClass.getTypeParameters()[0]);
            if (componentType != null) {
              getVariantsFromQualifierType(refExpr, processor, componentType, refExpr.getProject());
            }
          }
        }
      }
    } else if (qualifierType instanceof PsiArrayType) {
      getVariantsFromQualifierType(refExpr, processor, ((PsiArrayType) qualifierType).getComponentType(), refExpr.getProject());
    }
  }

  private static LookupElement[] addPretendedProperties(PsiElement[] elements) {
    List<LookupElement> result = new ArrayList<LookupElement>();
    final LookupElementFactory factory = LookupElementFactory.getInstance();

    for (PsiElement element : elements) {
      if (element instanceof PsiMethod) {
        PsiMethod method = (PsiMethod) element;
        if (PsiUtil.isSimplePropertyAccessor(method)) {
          String propName = PropertyUtil.getPropertyName(method);
          if (propName != null) {
            if (!PsiUtil.isValidReferenceName(propName)) {
              propName = "'" + propName + "'";
            }
            result.add(factory.createLookupElement(propName).setIcon(GroovyIcons.PROPERTY));
          }
        }
      }
    }

    return result.toArray(new LookupElement[result.size()]);
  }

  private static void getVariantsFromQualifier(GrReferenceExpression refExpr, ResolverProcessor processor, GrExpression qualifier) {
    Project project = qualifier.getProject();
    PsiType qualifierType = qualifier.getType();
    if (qualifierType == null) {
      if (qualifier instanceof GrReferenceExpression) {
        PsiElement resolved = ((GrReferenceExpression) qualifier).resolve();
        if (resolved instanceof PsiPackage) {
          resolved.processDeclarations(processor, PsiSubstitutor.EMPTY, null, refExpr);
          return;
        }
      }
      final PsiClassType type = refExpr.getManager().getElementFactory().createTypeByFQClassName(GrTypeDefinition.DEFAULT_BASE_CLASS_NAME, refExpr.getResolveScope());
      getVariantsFromQualifierType(refExpr, processor, type, project);
    } else {
      if (qualifierType instanceof PsiIntersectionType) {
        for (PsiType conjunct : ((PsiIntersectionType) qualifierType).getConjuncts()) {
          getVariantsFromQualifierType(refExpr, processor, conjunct, project);
        }
      } else {
        getVariantsFromQualifierType(refExpr, processor, qualifierType, project);
        if (qualifier instanceof GrReferenceExpression) {
          PsiElement resolved = ((GrReferenceExpression) qualifier).resolve();
          if (resolved instanceof PsiClass) { ////omitted .class
            GlobalSearchScope scope = refExpr.getResolveScope();
            PsiClass javaLangClass = PsiUtil.getJavaLangObject(resolved, scope);
            if (javaLangClass != null) {
              javaLangClass.processDeclarations(processor, PsiSubstitutor.EMPTY, null, refExpr);
            }
          }
        }
      }
    }
  }

  private static String[] getVariantsWithSameQualifier(GrExpression qualifier, GrReferenceExpression refExpr) {
    if (qualifier != null && qualifier.getType() != null) return ArrayUtil.EMPTY_STRING_ARRAY;

    final PsiElement scope = PsiTreeUtil.getParentOfType(refExpr, GrMember.class, GroovyFileBase.class);
    List<String> result = new ArrayList<String>();
    addVariantsWithSameQualifier(scope, refExpr, qualifier, result);
    return result.toArray(new String[result.size()]);
  }

  private static void addVariantsWithSameQualifier(PsiElement element,
                                                   GrReferenceExpression patternExpression,
                                                   GrExpression patternQualifier,
                                                   List<String> result) {
    if (element instanceof GrReferenceExpression && element != patternExpression
        && !PsiUtil.isLValue((GroovyPsiElement) element)) {
      final GrReferenceExpression refExpr = (GrReferenceExpression) element;
      final String refName = refExpr.getReferenceName();
      if (refName != null && refExpr.resolve() == null) {
        final GrExpression hisQualifier = refExpr.getQualifierExpression();
        if (hisQualifier != null && patternQualifier != null) {
          if (PsiEquivalenceUtil.areElementsEquivalent(hisQualifier, patternQualifier)) {
            result.add(refName);
          }
        } else if (hisQualifier == null && patternQualifier == null) {
          result.add(refName);
        }
      }
    }

    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      addVariantsWithSameQualifier(child, patternExpression, patternQualifier, result);
    }
  }

  private static void getVariantsFromQualifierType(GrReferenceExpression refExpr, ResolverProcessor processor, PsiType qualifierType, Project project) {
    if (qualifierType instanceof PsiClassType) {
      PsiClass qualifierClass = ((PsiClassType) qualifierType).resolve();
      if (qualifierClass != null) {
        qualifierClass.processDeclarations(processor, PsiSubstitutor.EMPTY, null, refExpr);
      }
      if (!ResolveUtil.processCategoryMembers(refExpr, processor, (PsiClassType) qualifierType)) return;
    } else if (qualifierType instanceof PsiArrayType) {
      final GrTypeDefinition arrayClass = GroovyPsiManager.getInstance(project).getArrayClass();
      if (!arrayClass.processDeclarations(processor, PsiSubstitutor.EMPTY, null, refExpr)) return;
    } else if (qualifierType instanceof PsiIntersectionType) {
      for (PsiType conjunct : ((PsiIntersectionType) qualifierType).getConjuncts()) {
        getVariantsFromQualifierType(refExpr, processor, conjunct, project);
      }
      return;
    }

    ResolveUtil.processDefaultMethods(qualifierType, processor, project);
  }
}
