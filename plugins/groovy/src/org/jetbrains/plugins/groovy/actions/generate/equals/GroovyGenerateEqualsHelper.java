/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.actions.generate.equals;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.actions.generate.GroovyCodeInsightBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.text.MessageFormat;
import java.util.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.06.2008
 */
public class GroovyGenerateEqualsHelper {
  private static final Logger LOG = Logger.getInstance(GroovyGenerateEqualsHelper.class);

  private final PsiClass myClass;
  private final PsiField[] myEqualsFields;
  private final PsiField[] myHashCodeFields;
  private final HashSet<PsiField> myNonNullSet;
  private final GroovyPsiElementFactory myFactory;
  private String myParameterName;

  @NonNls private static final String BASE_OBJECT_PARAMETER_NAME = "object";
  @NonNls private static final String BASE_OBJECT_LOCAL_NAME = "that";
  @NonNls private static final String RESULT_VARIABLE = "result";
  @NonNls private static final String TEMP_VARIABLE = "temp";

  private String myClassInstanceName;

  @NonNls
  private static final HashMap<String, MessageFormat> PRIMITIVE_HASHCODE_FORMAT = new HashMap<>();
  private final boolean mySuperHasHashCode;
//  private CodeStyleManager myCodeStyleManager;

  private final Project myProject;
  private final boolean myCheckParameterWithInstanceof;

  public GroovyGenerateEqualsHelper(Project project,
                                    PsiClass aClass,
                                    PsiField[] equalsFields,
                                    PsiField[] hashCodeFields,
                                    PsiField[] nonNullFields,
                                    boolean useInstanceofToCheckParameterType) {
    myClass = aClass;
    myEqualsFields = equalsFields;
    myHashCodeFields = hashCodeFields;
    myProject = project;
    myCheckParameterWithInstanceof = useInstanceofToCheckParameterType;

    myNonNullSet = new HashSet<>();
    ContainerUtil.addAll(myNonNullSet, nonNullFields);

    myFactory = GroovyPsiElementFactory.getInstance(project);

    mySuperHasHashCode = superMethodExists(getHashCodeSignature());
//    myCodeStyleManager = CodeStyleManager.getInstance(myProject);
  }

  private static String getUniqueLocalVarName(String base, PsiField[] fields) {
    String id = base;
    int index = 0;
    while (true) {
      if (index > 0) {
        id = base + index;
      }
      index++;
      boolean anyEqual = false;
      for (PsiField equalsField : fields) {
        if (id.equals(equalsField.getName())) {
          anyEqual = true;
          break;
        }
      }
      if (!anyEqual) break;
    }


    return id;
  }

  public void run() {
    try {
      final Collection<PsiMethod> members = generateMembers();
      for (PsiElement member : members) {
        myClass.add(member);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public Collection<PsiMethod> generateMembers() throws IncorrectOperationException {
    PsiMethod equals = null;
    if (myEqualsFields != null && findMethod(myClass, getEqualsSignature(myProject, myClass.getResolveScope())) == null) {
      equals = createEquals();
    }

    PsiMethod hashCode = null;
    if (myHashCodeFields != null && findMethod(myClass, getHashCodeSignature()) == null) {
      if (myHashCodeFields.length > 0) {
        hashCode = createHashCode();
      } else {
        hashCode = myFactory.createMethodFromText("int hashCode() {\nreturn 0\n}");
        if (!mySuperHasHashCode) {
          //    reformatCode(hashCode);
        }
      }
    }
    if (hashCode != null && equals != null) {
      return Arrays.asList(equals, hashCode);
    } else if (equals != null) {
      return Collections.singletonList(equals);
    } else if (hashCode != null) {
      return Collections.singletonList(hashCode);
    } else {
      return Collections.emptyList();
    }
  }


  private void addDoubleFieldComparison(final StringBuffer buffer, final PsiField field) {
    @NonNls final String type = PsiType.DOUBLE.equals(field.getType()) ? "Double" : "Float";
    final Object[] parameters = new Object[]{type, myClassInstanceName, field.getName()};
    DOUBLE_FIELD_COMPARER_MF.format(parameters, buffer, null);
  }

  @NonNls private static final MessageFormat ARRAY_COMPARER_MF = new MessageFormat("if (!java.util.Arrays.equals({1}, {0}.{1})) return false\n");
  @NonNls private static final MessageFormat FIELD_COMPARER_MF = new MessageFormat("if ({1} != {0}.{1}) return false\n");
  @NonNls private static final MessageFormat DOUBLE_FIELD_COMPARER_MF = new MessageFormat("if ({0}.compare({1}.{2}, {2}) != 0) return false\n");

  private void addArrayEquals(StringBuffer buffer, PsiField field) {
    final PsiType fieldType = field.getType();
    if (isNestedArray(fieldType)) {
      buffer.append(" ");
      buffer.append(GroovyCodeInsightBundle.message("generate.equals.compare.nested.arrays.comment", field.getName()));
      buffer.append("\n");
      return;
    }
    if (isArrayOfObjects(fieldType)) {
      buffer.append(" ");
      buffer.append(GroovyCodeInsightBundle.message("generate.equals.compare.arrays.comment"));
      buffer.append("\n");
    }

    ARRAY_COMPARER_MF.format(getComparerFormatParameters(field), buffer, null);
  }

  private Object[] getComparerFormatParameters(PsiField field) {
    return new Object[]{myClassInstanceName, field.getName()};
  }

  private void addFieldComparison(StringBuffer buffer, PsiField field) {
    FIELD_COMPARER_MF.format(getComparerFormatParameters(field), buffer, null);
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private void addInstanceOfToText(@NonNls StringBuffer buffer, String returnValue) {
    if (myCheckParameterWithInstanceof) {
      buffer.append("if (!(").append(myParameterName).append(" instanceof ").append(myClass.getName()).append(")) " + "return ")
        .append(returnValue).append('\n');
    } else {
      buffer.append("if (").append("getClass() != ").append(myParameterName).append(".class) " + "return ").append(returnValue)
        .append('\n');
    }
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private void addEqualsPrologue(@NonNls StringBuffer buffer) {
    buffer.append("if (this.is(").append(myParameterName).append(")").append(") return true\n");
    if (!superMethodExists(getEqualsSignature(myProject, myClass.getResolveScope()))) {
      addInstanceOfToText(buffer, Boolean.toString(false));
    } else {
      addInstanceOfToText(buffer, Boolean.toString(false));
      buffer.append("if (!super.equals(");
      buffer.append(myParameterName);
      buffer.append(")) return false\n");
    }
  }

  private void addClassInstance(@NonNls StringBuffer buffer) {
    buffer.append("\n");
    // A a = (A) object;
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(myProject);
    if (settings.GENERATE_FINAL_LOCALS) {
      buffer.append("final ");
    }

    buffer.append(myClass.getName());
    buffer.append(" ").append(myClassInstanceName).append(" = (");
    buffer.append(myClass.getName());
    buffer.append(")");
    buffer.append(myParameterName);
    buffer.append("\n\n");
  }

  private boolean superMethodExists(MethodSignature methodSignature) {
    LOG.assertTrue(myClass.isValid());
    PsiMethod superEquals = MethodSignatureUtil.findMethodBySignature(myClass, methodSignature, true);
    if (superEquals == null) return true;
    if (superEquals.hasModifierProperty(PsiModifier.ABSTRACT)) return false;
    return !CommonClassNames.JAVA_LANG_OBJECT.equals(superEquals.getContainingClass().getQualifiedName());
  }


  private PsiMethod createEquals() throws IncorrectOperationException {
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(myProject);
    String[] nameSuggestions = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, PsiType.getJavaLangObject(myClass.getManager(), myClass.getResolveScope())).names;
    final String objectBaseName = nameSuggestions.length > 0 ? nameSuggestions[0] : BASE_OBJECT_PARAMETER_NAME;
    myParameterName = getUniqueLocalVarName(objectBaseName, myEqualsFields);
    //todo isApplicable it
    final PsiType classType = TypesUtil.createType(myClass.getQualifiedName(), myClass.getContext());

    nameSuggestions = codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE, null, null, classType).names;
    String instanceBaseName = nameSuggestions.length > 0 && nameSuggestions[0].length() < 10 ? nameSuggestions[0] : BASE_OBJECT_LOCAL_NAME;
    myClassInstanceName = getUniqueLocalVarName(instanceBaseName, myEqualsFields);

    @NonNls StringBuffer buffer = new StringBuffer();
    buffer.append("boolean equals(").append(myParameterName).append(") {\n");
    addEqualsPrologue(buffer);
    if (myEqualsFields.length > 0) {
      addClassInstance(buffer);

      ArrayList<PsiField> equalsFields = new ArrayList<>();
      ContainerUtil.addAll(equalsFields, myEqualsFields);
      Collections.sort(equalsFields, EqualsFieldsComparator.INSTANCE);

      for (PsiField field : equalsFields) {
        if (!field.hasModifierProperty(PsiModifier.STATIC)) {
          final PsiType type = field.getType();
          if (type instanceof PsiArrayType) {
            addArrayEquals(buffer, field);
          }
          else if (type instanceof PsiPrimitiveType) {
            if (PsiType.DOUBLE.equals(type) || PsiType.FLOAT.equals(type)) {
              addDoubleFieldComparison(buffer, field);
            }
            else {
              addFieldComparison(buffer, field);
            }
          }
          else {
            if (type instanceof PsiClassType) {
              final PsiClass aClass = ((PsiClassType)type).resolve();
              if (aClass != null && aClass.isEnum()) {
                addFieldComparison(buffer, field);
                continue;
              }
            }
            addFieldComparison(buffer, field);
          }
        }
      }
    }
    buffer.append("\nreturn true\n}");

    GrMethod result = myFactory.createMethodFromText(buffer.toString());
    final PsiParameter parameter = result.getParameterList().getParameters()[0];
    PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL, CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS);

    try {
      result = ((GrMethod) CodeStyleManager.getInstance(myProject).reformat(result));
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    return result;
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private PsiMethod createHashCode() throws IncorrectOperationException {

    StringBuilder buffer = StringBuilderSpinAllocator.alloc();

    try {
      buffer.append("int hashCode() {\n");
      if (!mySuperHasHashCode && myHashCodeFields.length == 1) {
        PsiField field = myHashCodeFields[0];
        final String tempName = addTempForOneField(field, buffer);
        buffer.append("return ");
        if (field.getType() instanceof PsiPrimitiveType) {
          addPrimitiveFieldHashCode(buffer, field, tempName);
        } else {
          addFieldHashCode(buffer, field);
        }
        buffer.append("\n}");
      } else if (myHashCodeFields.length > 0) {
        CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(myProject);
        final String resultName = getUniqueLocalVarName(settings.LOCAL_VARIABLE_NAME_PREFIX + RESULT_VARIABLE, myHashCodeFields);

        buffer.append("int ");
        buffer.append(resultName);

        boolean resultAssigned = false;
        if (mySuperHasHashCode) {
          buffer.append(" = ");
          addSuperHashCode(buffer);
          resultAssigned = true;
        }
        buffer.append("\n");
        String tempName = addTempDeclaration(buffer);
        for (PsiField field : myHashCodeFields) {
          addTempAssignment(field, buffer, tempName);
          buffer.append(resultName);
          buffer.append(" = ");
          if (resultAssigned) {
            buffer.append("31 * ");
            buffer.append(resultName);
            buffer.append(" + ");
          }
          if (field.getType() instanceof PsiPrimitiveType) {
            addPrimitiveFieldHashCode(buffer, field, tempName);
          } else {
            addFieldHashCode(buffer, field);
          }
          buffer.append('\n');
          resultAssigned = true;
        }
        buffer.append("return ");
        buffer.append(resultName);
        buffer.append("\n}");
      } else {
        buffer.append("return 0\n}");
      }
      PsiMethod hashCode = myFactory.createMethodFromText(buffer.toString());

      try {
        hashCode = ((GrMethod) CodeStyleManager.getInstance(myProject).reformat(hashCode));
      } catch (IncorrectOperationException e) {
        LOG.error(e);
      }

//      reformatCode(hashCode);
      return hashCode;
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }
  }

  private static void addTempAssignment(PsiField field, StringBuilder buffer, String tempName) {
    if (PsiType.DOUBLE.equals(field.getType())) {
      buffer.append(tempName);
      addTempForDoubleInitialization(field, buffer);
    }
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private static void addTempForDoubleInitialization(PsiField field, StringBuilder buffer) {
    buffer.append(" = ").append(field.getName()).append(" != +0.0d ? Double.doubleToLongBits(");
    buffer.append(field.getName());
    buffer.append(") : 0L\n");
  }

  @Nullable
  @SuppressWarnings("HardCodedStringLiteral")
  private String addTempDeclaration(StringBuilder buffer) {
    for (PsiField hashCodeField : myHashCodeFields) {
      if (PsiType.DOUBLE.equals(hashCodeField.getType())) {
        final String name = getUniqueLocalVarName(TEMP_VARIABLE, myHashCodeFields);
        buffer.append("long ").append(name).append("\n");
        return name;
      }
    }
    return null;
  }

  @Nullable
  @SuppressWarnings("HardCodedStringLiteral")
  private String addTempForOneField(PsiField field, StringBuilder buffer) {
    if (PsiType.DOUBLE.equals(field.getType())) {
      final String name = getUniqueLocalVarName(TEMP_VARIABLE, myHashCodeFields);
      CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(myProject);
      if (settings.GENERATE_FINAL_LOCALS) {
        buffer.append("final ");
      }
      buffer.append("long ").append(name);
      addTempForDoubleInitialization(field, buffer);
      return name;
    }
    else {
      return null;
    }
  }

  private static void addPrimitiveFieldHashCode(StringBuilder buffer, PsiField field, String tempName) {
    MessageFormat format = PRIMITIVE_HASHCODE_FORMAT.get(field.getType().getCanonicalText());
    buffer.append(format.format(new Object[]{field.getName(), tempName}));
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private void addFieldHashCode(StringBuilder buffer, PsiField field) {
    final String name = field.getName();
    if (myNonNullSet.contains(field)) {
      adjustHashCodeToArrays(buffer, field, name);
    } else {
      buffer.append("(");
      buffer.append(name);
      buffer.append(" != null ? ");
      adjustHashCodeToArrays(buffer, field, name);
      buffer.append(" : 0)");
    }
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private static void adjustHashCodeToArrays(StringBuilder buffer, final PsiField field, final String name) {
    if (field.getType() instanceof PsiArrayType && LanguageLevel.JDK_1_5.compareTo(PsiUtil.getLanguageLevel(field)) <= 0) {
      buffer.append("Arrays.hashCode(");
      buffer.append(name);
      buffer.append(")");
    } else {
      buffer.append(name);
      buffer.append(".hashCode()");
    }
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private void addSuperHashCode(StringBuilder buffer) {
    if (mySuperHasHashCode) {
      buffer.append("super.hashCode()");
    } else {
      buffer.append("0");
    }
  }

  @Nullable
  static PsiMethod findMethod(PsiClass aClass, MethodSignature signature) {
    return MethodSignatureUtil.findMethodBySignature(aClass, signature, false);
  }

  static class EqualsFieldsComparator implements Comparator<PsiField> {
    public static final EqualsFieldsComparator INSTANCE = new EqualsFieldsComparator();

    @Override
    public int compare(PsiField f1, PsiField f2) {
      if (f1.getType() instanceof PsiPrimitiveType && !(f2.getType() instanceof PsiPrimitiveType)) return -1;
      if (!(f1.getType() instanceof PsiPrimitiveType) && f2.getType() instanceof PsiPrimitiveType) return 1;
      final String name1 = f1.getName();
      final String name2 = f2.getName();
      assert name1 != null && name2 != null;
      return name1.compareTo(name2);
    }
  }

  static {
    initPrimitiveHashcodeFormats();
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private static void initPrimitiveHashcodeFormats() {
    PRIMITIVE_HASHCODE_FORMAT.put("byte", new MessageFormat("(int) {0}"));
    PRIMITIVE_HASHCODE_FORMAT.put("short", new MessageFormat("(int) {0}"));
    PRIMITIVE_HASHCODE_FORMAT.put("int", new MessageFormat("{0}"));
    PRIMITIVE_HASHCODE_FORMAT.put("long", new MessageFormat("(int) ({0} ^ ({0} >>> 32))"));
    PRIMITIVE_HASHCODE_FORMAT.put("boolean", new MessageFormat("({0} ? 1 : 0)"));

    PRIMITIVE_HASHCODE_FORMAT.put("float", new MessageFormat("({0} != +0.0f ? Float.floatToIntBits({0}) : 0)"));
    PRIMITIVE_HASHCODE_FORMAT.put("double", new MessageFormat("(int) ({1} ^ ({1} >>> 32))"));

    PRIMITIVE_HASHCODE_FORMAT.put("char", new MessageFormat("(int) {0}"));
    PRIMITIVE_HASHCODE_FORMAT.put("void", new MessageFormat("0"));
    PRIMITIVE_HASHCODE_FORMAT.put("void", new MessageFormat("({0} ? 1 : 0)"));
  }

  public static boolean isNestedArray(PsiType aType) {
    if (!(aType instanceof PsiArrayType)) return false;
    final PsiType componentType = ((PsiArrayType) aType).getComponentType();
    return componentType instanceof PsiArrayType;
  }

  public static boolean isArrayOfObjects(PsiType aType) {
    if (!(aType instanceof PsiArrayType)) return false;
    final PsiType componentType = ((PsiArrayType) aType).getComponentType();
    final PsiClass psiClass = PsiUtil.resolveClassInType(componentType);
    if (psiClass == null) return false;
    final String qName = psiClass.getQualifiedName();
    return CommonClassNames.JAVA_LANG_OBJECT.equals(qName);
  }

  public static MethodSignature getHashCodeSignature() {
    return MethodSignatureUtil.createMethodSignature("hashCode", PsiType.EMPTY_ARRAY, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
  }

  public static MethodSignature getEqualsSignature(Project project, GlobalSearchScope scope) {
    final PsiClassType javaLangObject = PsiType.getJavaLangObject(PsiManager.getInstance(project), scope);
    return MethodSignatureUtil
        .createMethodSignature("equals", new PsiType[]{javaLangObject}, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY);
  }
}
