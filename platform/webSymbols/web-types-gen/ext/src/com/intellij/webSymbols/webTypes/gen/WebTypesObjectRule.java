package com.intellij.webSymbols.webTypes.gen;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.codemodel.*;
import org.jsonschema2pojo.Annotator;
import org.jsonschema2pojo.Schema;
import org.jsonschema2pojo.exception.ClassAlreadyExistsException;
import org.jsonschema2pojo.exception.GenerationException;
import org.jsonschema2pojo.rules.ObjectRule;
import org.jsonschema2pojo.util.ParcelableHelper;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.jsonschema2pojo.util.TypeUtil.resolveType;

@SuppressWarnings("SSBasedInspection")
public class WebTypesObjectRule extends ObjectRule {

  private static final Map<String, Consumer<JDefinedClass>> interfaceImplementation = new HashMap<>();

  private final WebTypesReflectionHelper reflectionHelper;
  private final WebTypesRuleFactory ruleFactory;

  protected WebTypesObjectRule(WebTypesRuleFactory ruleFactory,
                               ParcelableHelper parcelableHelper,
                               WebTypesReflectionHelper reflectionHelper) {
    super(ruleFactory, parcelableHelper, reflectionHelper);
    this.reflectionHelper = reflectionHelper;
    this.ruleFactory = ruleFactory;
  }

  @Override
  public JType apply(String nodeName,
                     JsonNode node,
                     JsonNode parent,
                     JPackage _package,
                     Schema schema) {
    JsonNode isInterface = node.get("javaInterface");
    if (isInterface != null && isInterface.asBoolean(false)) {
      JDefinedClass jInterface;
      try {
        jInterface = createInterface(nodeName, node, _package);
        registerImplementation(jInterface, nodeName, node, schema);
      }
      catch (ClassAlreadyExistsException e) {
        JType result = e.getExistingClass();
        if (result instanceof JDefinedClass && ((JDefinedClass)result).isInterface()) {
          registerImplementation((JDefinedClass)result, nodeName, node, schema);
        }
        return result;
      }

      addImplements(jInterface, nodeName, node, _package, schema);
      schema.setJavaTypeIfEmpty(jInterface);

      if (node.has("title")) {
        ruleFactory.getTitleRule().apply(nodeName, node.get("title"), node, jInterface, schema);
      }

      if (node.has("description")) {
        ruleFactory.getDescriptionRule().apply(nodeName, node.get("description"), node, jInterface, schema);
      }
      ruleFactory.getPropertiesRule().apply(nodeName, node.get("properties"), node, jInterface, schema);
      if (node.has("patternProperties")) {
        ruleFactory.getPatternPropertiesRule().apply(nodeName, node.get("patternProperties"), node, jInterface, schema);
      }
      else {
        ruleFactory.getAdditionalPropertiesRule().apply(nodeName, node.get("additionalProperties"), node, jInterface, schema);
      }

      if (node.has("javaInterfaces")) {
        addInterfaces(jInterface, node.get("javaInterfaces"));
      }

      new ArrayList<>(jInterface.fields().values()).forEach(field -> jInterface.removeField(field));
      jInterface.methods().forEach(method -> removeBody(method));
    }
    JType result = super.apply(nodeName, node, parent, _package, schema);
    if (result instanceof JDefinedClass) {
      JDefinedClass jClass = (JDefinedClass)result;
      addImplements(jClass, nodeName, node, _package, schema);
      if (node.has("patternProperties")) {
        removeAdditionalProperties(jClass);
        ruleFactory.getPatternPropertiesRule().apply(nodeName, node.get("patternProperties"), node, jClass, schema);
      }
      JsonNode isAbstract = node.get("javaAbstract");
      if (isAbstract != null && isAbstract.isBoolean() && isAbstract.asBoolean()) {
        setAbstract(jClass.mods());
        removeAdditionalProperties(jClass);
      }
    }
    return result;
  }

  private void registerImplementation(JDefinedClass jInterface,
                                      String nodeName,
                                      JsonNode node,
                                      Schema schema) {
    interfaceImplementation.put(jInterface.fullName(), (jClass) -> {
      ruleFactory.getPropertiesRule().apply(nodeName, node.get("properties"), node, jClass, schema);
      removeAdditionalProperties(jClass);
      if (node.has("patternProperties")) {
        ruleFactory.getPatternPropertiesRule().apply(nodeName, node.get("patternProperties"), node, jClass, schema);
      }
      else {
        ruleFactory.getAdditionalPropertiesRule().apply(nodeName, node.get("additionalProperties"), node, jClass, schema);
      }
    });
  }

  private static void removeAdditionalProperties(JDefinedClass jClass) {
    JFieldVar var = jClass.fields().get("additionalProperties");
    if (var != null) {
      jClass.removeField(var);
      jClass.methods().removeIf(m -> m.name().equals("getAdditionalProperties") || m.name().equals("setAdditionalProperty"));
    }
  }

  private JDefinedClass createInterface(String nodeName, JsonNode node, JPackage _package) throws ClassAlreadyExistsException {
    JDefinedClass newType;

    Annotator annotator = ruleFactory.getAnnotator();

    try {
      newType = _package._class(JMod.PUBLIC, ruleFactory.getNameHelper().getUniqueClassName(nodeName, node, _package), ClassType.INTERFACE);
    }
    catch (JClassAlreadyExistsException e) {
      throw new ClassAlreadyExistsException(e.getExistingClass());
    }
    annotator.typeInfo(newType, node);
    annotator.propertyInclusion(newType, node);
    return newType;
  }

  static void addInterfaces(JDefinedClass jclass, JsonNode javaInterfaces) {
    for (JsonNode i : javaInterfaces) {
      jclass._implements(resolveType(jclass._package(), i.asText()));
    }
  }

  private void addImplements(JDefinedClass jClass, String nodeName, JsonNode node, JPackage jPackage, Schema schema) {
    List<JType> superTypes = reflectionHelper.getSuperTypesFromAllAnyOne(nodeName, node, jPackage, schema);
    for (JType superType : superTypes) {
      if (superType instanceof JClass && ((JClass)superType).isInterface()) {
        jClass._implements((JClass)superType);
        if (!jClass.isInterface()) {
          // apply implementation here
          Consumer<JDefinedClass> implementation = interfaceImplementation.get(superType.fullName());
          if (implementation == null) {
            this.ruleFactory.getLogger().error("Cannot implement interface " + superType.fullName() + " of " + jClass.fullName());
          }
          else {
            implementation.accept(jClass);
            mergeJsonPropertyOrder(jClass);
          }
        }
      }
    }
  }

  private static void mergeJsonPropertyOrder(JDefinedClass aClass) {
    List<JAnnotationUse> jsonPropertyOrders = aClass.annotations().stream()
      .filter(it -> it.getAnnotationClass().fullName().equals("com.fasterxml.jackson.annotation.JsonPropertyOrder"))
      .collect(Collectors.toList());
    if (jsonPropertyOrders.size() > 1) {
      JAnnotationUse first = jsonPropertyOrders.get(0);
      JAnnotationArrayMember value = (JAnnotationArrayMember)first.getAnnotationMembers().get("value");
      for (int i = 1; i < jsonPropertyOrders.size(); i++) {
        JAnnotationUse toMerge = jsonPropertyOrders.get(1);
        ((JAnnotationArrayMember)toMerge.getAnnotationMembers().get("value")).annotations()
          .forEach((JAnnotationValue a) -> addAnnotationValue(value, a));
        removeAnnotation(aClass, toMerge);
      }
    }
  }

  private static void addAnnotationValue(JAnnotationArrayMember aClass, JAnnotationValue toAdd) {
    try {
      Field field = JAnnotationArrayMember.class.getDeclaredField("values");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      List<JAnnotationValue> annotations = (List<JAnnotationValue>)field.get(aClass);
      annotations.add(toAdd);
    }
    catch (NoSuchFieldException e) {
      throw new GenerationException(e);
    }
    catch (IllegalAccessException e) {
      throw new GenerationException(e);
    }
  }

  private static void removeAnnotation(JDefinedClass aClass, JAnnotationUse toRemove) {
    try {
      Field field = JDefinedClass.class.getDeclaredField("annotations");
      field.setAccessible(true);
      List<?> annotations = (List<?>)field.get(aClass);
      annotations.remove(toRemove);
    }
    catch (NoSuchFieldException e) {
      throw new GenerationException(e);
    }
    catch (IllegalAccessException e) {
      throw new GenerationException(e);
    }
  }

  private static void removeBody(JMethod method) {
    try {
      Field field = JMethod.class.getDeclaredField("body");
      field.setAccessible(true);
      field.set(method, null);
    }
    catch (NoSuchFieldException e) {
      throw new GenerationException(e);
    }
    catch (IllegalAccessException e) {
      throw new GenerationException(e);
    }
  }

  private static void setAbstract(JMods mods) {
    try {
      Method method = JMods.class.getDeclaredMethod("setFlag", int.class, boolean.class);
      method.setAccessible(true);
      method.invoke(mods, JMod.ABSTRACT, true);
    }
    catch (NoSuchMethodException e) {
      throw new GenerationException(e);
    }
    catch (InvocationTargetException e) {
      throw new GenerationException(e);
    }
    catch (IllegalAccessException e) {
      throw new GenerationException(e);
    }
  }
}
