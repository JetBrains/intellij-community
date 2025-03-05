// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.AntIntrospector;
import com.intellij.lang.ant.ReflectedProject;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomTarget;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
public final class AntDomExtender extends DomExtender<AntDomElement> {
  private static final Logger LOG = Logger.getInstance(AntDomExtender.class);

  private static final Key<Class<?>> ELEMENT_IMPL_CLASS_KEY = Key.create("_element_impl_class_");

  private static final Map<String, Class<? extends AntDomElement>> TAG_MAPPING = Map.ofEntries(
    Map.entry("property", AntDomProperty.class),
    Map.entry("dirname", AntDomDirname.class),
    Map.entry("fileset", AntDomFileSet.class),
    Map.entry("dirset", AntDomDirSet.class),
    Map.entry("filelist", AntDomFileList.class),
    Map.entry("pathelement", AntDomPathElement.class),
    Map.entry("path", AntDomPath.class),
    Map.entry("classpath", AntDomClasspath.class),
    Map.entry("typedef", AntDomTypeDef.class),
    Map.entry("taskdef", AntDomTaskdef.class),
    Map.entry("presetdef", AntDomPresetDef.class),
    Map.entry("macrodef", AntDomMacroDef.class),
    Map.entry("scriptdef", AntDomScriptDef.class),
    Map.entry("antlib", AntDomAntlib.class),
    Map.entry("ant", AntDomAnt.class),
    Map.entry("antcall", AntDomAntCall.class),
    Map.entry("available", AntDomPropertyDefiningTaskWithDefaultValue.class),
    Map.entry("condition", AntDomPropertyDefiningTaskWithDefaultValue.class),
    Map.entry("uptodate", AntDomPropertyDefiningTaskWithDefaultValue.class),
    Map.entry("checksum", AntDomChecksumTask.class),
    Map.entry("loadfile", AntDomLoadFileTask.class),
    Map.entry("whichresource", AntDomWhichResourceTask.class),
    Map.entry("jarlib-resolve", AntDomPropertyDefiningTask.class),
    Map.entry("p4counter", AntDomPropertyDefiningTask.class),
    Map.entry("pathconvert", AntDomPropertyDefiningTask.class),
    Map.entry("basename", AntDomBasenameTask.class),
    Map.entry("length", AntDomLengthTask.class),
    Map.entry("tempfile", AntDomTempFile.class),
    Map.entry("exec", AntDomExecTask.class),
    Map.entry("buildnumber", AntDomBuildnumberTask.class),
    Map.entry("tstamp", AntDomTimestampTask.class),
    Map.entry("format", AntDomTimestampTaskFormat.class),
    Map.entry("input", AntDomInputTask.class)
  );

  @Override
  public void registerExtensions(final @NotNull AntDomElement antDomElement, @NotNull DomExtensionsRegistrar registrar) {
    final XmlElement xmlElement = antDomElement.getXmlElement();
    if (xmlElement instanceof XmlTag xmlTag) {
      final String tagName = xmlTag.getName();

      final AntDomProject antProject = antDomElement.getAntProject();
      if (antProject == null) {
        return;
      }
      final ReflectedProject reflected = ReflectedProject.getProject(antProject.getClassLoader());
      if (reflected.getProject() == null) {
        return;
      }

      final DomGenericInfo genericInfo = antDomElement.getGenericInfo();
      AntIntrospector classBasedIntrospector = null;
      final Map<String, Class<?>> coreTaskDefs = reflected.getTaskDefinitions();
      final Map<String, Class<?>> coreTypeDefs = reflected.getDataTypeDefinitions();
      final boolean isCustom = antDomElement instanceof AntDomCustomElement;
      if ("project".equals(tagName)) {
        classBasedIntrospector = getIntrospector(reflected.getProject().getClass());
      }
      else if ("target".equals(tagName)) {
        classBasedIntrospector = getIntrospector(reflected.getTargetClass());
      }
      else {
        if (isCustom) {
          final AntDomCustomElement custom = (AntDomCustomElement)antDomElement;
          final Class definitionClass = custom.getDefinitionClass();
          if (definitionClass != null) {
            classBasedIntrospector = getIntrospector(definitionClass);
          }
        }
        else {
          Class elemType = antDomElement.getChildDescription().getUserData(ELEMENT_IMPL_CLASS_KEY);

          if (elemType == null) {
            if (coreTaskDefs != null) {
              elemType = coreTaskDefs.get(tagName);
            }
          }

          if (elemType == null) {
            if (coreTypeDefs != null) {
              elemType = coreTypeDefs.get(tagName);
            }
          }

          if (elemType != null) {
            classBasedIntrospector = getIntrospector(elemType);
          }
        }
      }

      AbstractIntrospector parentIntrospector = null;
      if (classBasedIntrospector != null) {
        parentIntrospector =
          new ClassIntrospectorAdapter(classBasedIntrospector, coreTaskDefs, coreTypeDefs, reflected.getRestrictedDefinitions());
      }
      else {
        if (isCustom) {
          final AntDomNamedElement declaringElement = ((AntDomCustomElement)antDomElement).getDeclaringElement();
          if (declaringElement instanceof AntDomMacroDef) {
            parentIntrospector = new MacrodefIntrospectorAdapter((AntDomMacroDef)declaringElement);
          }
          else if (declaringElement instanceof AntDomMacrodefElement) {
            parentIntrospector = new MacrodefElementOccurrenceIntrospectorAdapter(
              (AntDomMacrodefElement)declaringElement)/*ContainerElementIntrospector.INSTANCE*/;
          }
          else if (declaringElement instanceof AntDomScriptDef) {
            parentIntrospector = new ScriptdefIntrospectorAdapter((AntDomScriptDef)declaringElement);
          }
        }
      }

      if (parentIntrospector != null) {

        defineAttributes(xmlTag, registrar, genericInfo, parentIntrospector);

        if ("project".equals(tagName) || parentIntrospector.isContainer()) { // can contain any task or/and type definition
          if (coreTaskDefs != null) {
            for (Map.Entry<String, Class<?>> entry : coreTaskDefs.entrySet()) {
              final DomExtension extension = registerChild(registrar, genericInfo, entry.getKey());
              if (extension != null) {
                final Class type = entry.getValue();
                if (type != null) {
                  extension.putUserData(ELEMENT_IMPL_CLASS_KEY, type);
                }
                extension.putUserData(AntDomElement.ROLE, AntDomElement.Role.TASK);
              }
            }
          }
          if (coreTypeDefs != null) {
            for (Map.Entry<String, Class<?>> entry : coreTypeDefs.entrySet()) {
              final DomExtension extension = registerChild(registrar, genericInfo, entry.getKey());
              if (extension != null) {
                final Class type = entry.getValue();
                if (type != null) {
                  extension.putUserData(ELEMENT_IMPL_CLASS_KEY, type);
                }
                extension.putUserData(AntDomElement.ROLE, AntDomElement.Role.DATA_TYPE);
              }
            }
          }
        }
        else {
          final Iterator<String> nested = parentIntrospector.getNestedElementsIterator();
          while (nested.hasNext()) {
            final String nestedElementName = nested.next();
            final DomExtension extension = registerChild(registrar, genericInfo, nestedElementName);
            if (extension != null) {
              Class type = parentIntrospector.getNestedElementType(nestedElementName);
              if (type != null && CommonClassNames.JAVA_LANG_OBJECT.equals(type.getName())) {
                type = null; // hack to support badly written tasks
              }
              if (type == null) {
                if (coreTypeDefs != null) {
                  type = coreTypeDefs.get(nestedElementName);
                }
              }
              if (type != null) {
                extension.putUserData(ELEMENT_IMPL_CLASS_KEY, type);
              }
              AntDomElement.Role role = AntDomElement.Role.DATA_TYPE;
              if (coreTaskDefs != null && coreTaskDefs.containsKey(nestedElementName) ||
                  type != null && isAssignableFrom("org.apache.tools.ant.Task", type)) {
                role = AntDomElement.Role.TASK;
              }
              extension.putUserData(AntDomElement.ROLE, role);
            }
          }
        }
        registrar.registerCustomChildrenExtension(AntDomCustomElement.class, new AntCustomTagNameDescriptor());
      }
    }
  }

  private static void defineAttributes(XmlTag xmlTag,
                                       DomExtensionsRegistrar registrar,
                                       DomGenericInfo genericInfo,
                                       AbstractIntrospector parentIntrospector) {
    final Map<String, Pair<Type, Class<?>>> registeredAttribs = getStaticallyRegisteredAttributes(genericInfo);
    // define attributes discovered by introspector and not yet defined statically
    final Iterator<String> introspectedAttributes = parentIntrospector.getAttributesIterator();
    while (introspectedAttributes.hasNext()) {
      final String attribName = introspectedAttributes.next();
      if (genericInfo.getAttributeChildDescription(attribName) == null) { // if not defined yet
        final String _attribName = StringUtil.toLowerCase(attribName);
        final Pair<Type, Class<?>> types = registeredAttribs.get(_attribName);
        Type type = Pair.getFirst(types);
        Class<?> converterClass = Pair.getSecond(types);
        if (type == null) {
          type = String.class; // use String by default
          final Class attributeType = parentIntrospector.getAttributeType(attribName);
          if (attributeType != null) {
            // handle well-known types
            if (File.class.isAssignableFrom(attributeType)) {
              type = PsiFileSystemItem.class;
              converterClass = AntPathConverter.class;
            }
            else if (Boolean.class.isAssignableFrom(attributeType)) {
              type = Boolean.class;
              converterClass = AntBooleanConverter.class;
            }
            else if (isAssignableFrom("org.apache.tools.ant.types.Reference", attributeType)) {
              converterClass = AntDomRefIdConverter.class;
            }
          }
        }

        registerAttribute(registrar, attribName, type, converterClass);
        if (types == null) { // augment the map if this was a newly added attribute
          registeredAttribs.put(_attribName, new Pair<>(type, converterClass));
        }
      }
    }
    // handle attribute case problems:
    // additionaly register all attributes that exist in XML but differ from the registered ones only in case
    for (XmlAttribute xmlAttribute : xmlTag.getAttributes()) {
      final String existingAttribName = xmlAttribute.getName();
      if (genericInfo.getAttributeChildDescription(existingAttribName) == null) {
        final Pair<Type, Class<?>> pair = registeredAttribs.get(StringUtil.toLowerCase(existingAttribName));
        if (pair != null) { // if such attribute should actually be here
          registerAttribute(registrar, existingAttribName, pair.getFirst(), pair.getSecond());
        }
      }
    }
  }

  private static void registerAttribute(DomExtensionsRegistrar registrar,
                                        String attribName,
                                        final @NotNull Type attributeType,
                                        final @Nullable Class converterType) {
    final DomExtension extension = registrar.registerGenericAttributeValueChildExtension(new XmlName(attribName), attributeType);
    if (converterType != null) {
      try {
        extension.setConverter((Converter<?>)converterType.newInstance());
      }
      catch (InstantiationException | IllegalAccessException e) {
        LOG.info(e);
      }
    }
  }

  private static Map<String, Pair<Type, Class<?>>> getStaticallyRegisteredAttributes(final DomGenericInfo genericInfo) {
    final Map<String, Pair<Type, Class<?>>> map = new HashMap<>();
    for (DomAttributeChildDescription<?> description : genericInfo.getAttributeChildrenDescriptions()) {
      final Type type = description.getType();
      if (type instanceof ParameterizedType) {
        final Type[] typeArguments = ((ParameterizedType)type).getActualTypeArguments();
        if (typeArguments.length == 1) {
          String name = description.getXmlElementName();
          final Type attribType = typeArguments[0];
          Class<? extends Converter> converterType = null;
          final Convert converterAnnotation = description.getAnnotation(Convert.class);
          if (converterAnnotation != null) {
            converterType = converterAnnotation.value();
          }
          map.put(StringUtil.toLowerCase(name), new Pair<>(attribType, converterType));
        }
      }
    }
    return map;
  }

  private static @Nullable DomExtension registerChild(DomExtensionsRegistrar registrar, DomGenericInfo elementInfo, String childName) {
    if (elementInfo.getCollectionChildDescription(childName) == null) { // register if not yet defined statically
      Class<? extends AntDomElement> modelClass = getModelClass(childName);
      if (modelClass == null) {
        modelClass = AntDomElement.class;
      }
      return registrar.registerCollectionChildrenExtension(new XmlName(childName), modelClass);
    }
    return null;
  }

  public static @Nullable AntIntrospector getIntrospector(Class c) {
    try {
      return AntIntrospector.getInstance(c);
    }
    catch (Throwable e) {
      if (e instanceof ControlFlowException) {
        throw e;
      }
      LOG.warn("Unable to get Ant introspector", e);
    }
    return null;
  }

  private static @Nullable Class<? extends AntDomElement> getModelClass(@NotNull String tagName) {
    return TAG_MAPPING.get(StringUtil.toLowerCase(tagName));
  }

  private static boolean isAssignableFrom(final String baseClassName, final Class clazz) {
    try {
      final ClassLoader loader = clazz.getClassLoader();
      if (loader != null) {
        final Class<?> baseClass = loader.loadClass(baseClassName);
        return baseClass.isAssignableFrom(clazz);
      }
    }
    catch (ClassNotFoundException ignored) {
    }
    return false;
  }

  private static class AntCustomTagNameDescriptor extends CustomDomChildrenDescription.TagNameDescriptor {

    @Override
    public Set<EvaluatedXmlName> getCompletionVariants(@NotNull DomElement parent) {
      if (!(parent instanceof AntDomElement element)) {
        return Collections.emptySet();
      }
      final AntDomProject antDomProject = element.getAntProject();
      if (antDomProject == null) {
        return Collections.emptySet();
      }
      final CustomAntElementsRegistry registry = CustomAntElementsRegistry.getInstance(antDomProject);
      final Set<EvaluatedXmlName> result = new HashSet<>();
      for (XmlName variant : registry.getCompletionVariants(element)) {
        final String ns = variant.getNamespaceKey();
        result.add(new DummyEvaluatedXmlName(variant, ns != null ? ns : ""));
      }
      return result;
    }

    @Override
    public @Nullable PomTarget findDeclaration(DomElement parent, @NotNull EvaluatedXmlName name) {
      final XmlName xmlName = name.getXmlName();
      return doFindDeclaration(parent, xmlName);
    }

    @Override
    public @Nullable PomTarget findDeclaration(@NotNull DomElement child) {
      XmlName name = new XmlName(child.getXmlElementName(), child.getXmlElementNamespace());
      return doFindDeclaration(child.getParent(), name);
    }

    private static @Nullable PomTarget doFindDeclaration(DomElement parent, XmlName xmlName) {
      if (!(parent instanceof AntDomElement parentElement)) {
        return null;
      }
      final AntDomProject antDomProject = parentElement.getAntProject();
      if (antDomProject == null) {
        return null;
      }
      final CustomAntElementsRegistry registry = CustomAntElementsRegistry.getInstance(antDomProject);
      final AntDomElement declaringElement = registry.findDeclaringElement(parentElement, xmlName);
      if (declaringElement == null) {
        return null;
      }
      DomTarget target = DomTarget.getTarget(declaringElement);
      if (target == null && declaringElement instanceof AntDomTypeDef typedef) {
        final GenericAttributeValue<PsiFileSystemItem> resource = typedef.getResource();
        if (resource != null) {
          target = DomTarget.getTarget(declaringElement, resource);
        }
        if (target == null) {
          final GenericAttributeValue<PsiFileSystemItem> file = typedef.getFile();
          if (file != null) {
            target = DomTarget.getTarget(declaringElement, file);
          }
        }
      }
      return target;
    }
  }

  private abstract static class AbstractIntrospector {
    public @NotNull Iterator<String> getAttributesIterator() {
      return Collections.emptyIterator();
    }

    public @NotNull Iterator<String> getNestedElementsIterator() {
      return Collections.emptyIterator();
    }

    public abstract boolean isContainer();

    public @Nullable Class getAttributeType(String attribName) {
      return null;
    }

    public @Nullable Class getNestedElementType(String elementName) {
      return null;
    }
  }

  private static final class ClassIntrospectorAdapter extends AbstractIntrospector {
    private final AntIntrospector myIntrospector;
    private final Map<String, Class<?>> myCoreTaskDefs;
    private final Map<String, Class<?>> myCoreTypeDefs;
    private final Map<String, Collection<Class<?>>> myRestrictedDefinitions;
    private List<String> myNestedElements;
    private Map<String, Class<?>> myNestedElementTypes;

    private ClassIntrospectorAdapter(AntIntrospector introspector) {
      this(introspector, null, null, null);
    }

    ClassIntrospectorAdapter(AntIntrospector introspector,
                             Map<String, Class<?>> coreTaskDefs,
                             Map<String, Class<?>> coreTypeDefs,
                             Map<String, Collection<Class<?>>> restrictedDefinitions) {
      myIntrospector = introspector;
      myCoreTaskDefs = coreTaskDefs != null ? Collections.unmodifiableMap(coreTaskDefs) : Collections.emptyMap();
      myCoreTypeDefs = coreTypeDefs != null ? Collections.unmodifiableMap(coreTypeDefs) : Collections.emptyMap();
      myRestrictedDefinitions = restrictedDefinitions != null ? Collections.unmodifiableMap(restrictedDefinitions) : Collections.emptyMap();
    }

    @Override
    public @NotNull Iterator<String> getAttributesIterator() {
      return new EnumerationToIteratorAdapter<>(myIntrospector.getAttributes());
    }

    @Override
    public Class getAttributeType(String attribName) {
      return myIntrospector.getAttributeType(attribName);
    }

    @Override
    public boolean isContainer() {
      return myIntrospector.isContainer();
    }

    @Override
    public @NotNull Iterator<String> getNestedElementsIterator() {
      initNestedElements();
      return myNestedElements.iterator();
    }

    @Override
    public Class getNestedElementType(String attribName) {
      initNestedElements();
      return myNestedElementTypes.get(attribName);
    }

    private void initNestedElements() {
      if (myNestedElements != null) {
        return;
      }
      myNestedElements = new ArrayList<>();
      myNestedElementTypes = new HashMap<>();
      final Enumeration<String> nestedElements = myIntrospector.getNestedElements();
      while (nestedElements.hasMoreElements()) {
        final String elemName = nestedElements.nextElement();
        myNestedElements.add(elemName);
        myNestedElementTypes.put(elemName, myIntrospector.getElementType(elemName));
      }

      final Set<String> extensionPointTypes = myIntrospector.getExtensionPointTypes();
      for (String extPoint : extensionPointTypes) {
        processEntries(extPoint, myCoreTaskDefs);
        processEntries(extPoint, myCoreTypeDefs);
        processRestrictedTypeDefinitions(extPoint);
      }
    }

    private void processEntries(String extPoint, Map<String, Class<?>> definitions) {
      for (Map.Entry<String, Class<?>> entry : definitions.entrySet()) {
        final Class taskClass = entry.getValue();
        if (isAssignableFrom(extPoint, taskClass)) {
          final String elementName = entry.getKey();
          myNestedElements.add(elementName);
          myNestedElementTypes.put(elementName, taskClass);
        }
      }
    }

    private void processRestrictedTypeDefinitions(String extPoint) {
      for (Map.Entry<String, Collection<Class<?>>> entry : myRestrictedDefinitions.entrySet()) {
        for (Class<?> typeClass : entry.getValue()) {
          final String elementName = entry.getKey();
          if (!myNestedElementTypes.containsKey(elementName) && isAssignableFrom(extPoint, typeClass)) {
            myNestedElements.add(elementName);
            myNestedElementTypes.put(elementName, typeClass);
            break;
          }
        }
      }
    }
  }

  private static final class MacrodefIntrospectorAdapter extends AbstractIntrospector {
    private final AntDomMacroDef myMacrodef;

    private MacrodefIntrospectorAdapter(AntDomMacroDef macrodef) {
      myMacrodef = macrodef;
    }

    @Override
    public @NotNull Iterator<String> getAttributesIterator() {
      final List<AntDomMacrodefAttribute> macrodefAttributes = myMacrodef.getMacroAttributes();
      if (macrodefAttributes.isEmpty()) {
        return Collections.emptyIterator();
      }
      final List<String> attribs = new ArrayList<>(macrodefAttributes.size());
      for (AntDomMacrodefAttribute attribute : macrodefAttributes) {
        final String attribName = attribute.getName().getRawText();
        if (attribName != null) {
          attribs.add(attribName);
        }
      }
      return attribs.iterator();
    }

    @Override
    public boolean isContainer() {
      return myMacrodef.getMacroElements().stream()
        .map(AntDomMacrodefElement::isImplicit)
        .anyMatch(implicit -> implicit != null && Boolean.TRUE.equals(implicit.getValue()));
    }
  }

  private static final class MacrodefElementOccurrenceIntrospectorAdapter extends AbstractIntrospector {
    private final AntDomMacrodefElement myElement;
    private volatile List<AbstractIntrospector> myContexts;
    private volatile Map<String, Class> myChildrenMap;

    private MacrodefElementOccurrenceIntrospectorAdapter(AntDomMacrodefElement element) {
      myElement = element;
    }

    @Override
    public boolean isContainer() {
      return getContexts().stream().allMatch(AbstractIntrospector::isContainer);
    }

    @Override
    public @NotNull Iterator<String> getNestedElementsIterator() {
      return getNestedElementsMap().keySet().iterator();
    }

    @Override
    public Class getNestedElementType(String elementName) {
      return getNestedElementsMap().get(elementName);
    }

    private Map<String, Class> getNestedElementsMap() {
      if (myChildrenMap != null) {
        return myChildrenMap;
      }
      final List<AbstractIntrospector> contexts = getContexts();
      Map<String, Class> names = null;
      for (AbstractIntrospector context : contexts) {
        if (context.isContainer()) {
          continue;
        }
        final Set<String> set = new HashSet<>();
        for (Iterator<String> it = context.getNestedElementsIterator(); it.hasNext(); ) {
          set.add(it.next());
        }
        if (names == null) {
          names = new HashMap<>();
          for (String s : set) {
            names.put(s, context.getNestedElementType(s));
          }
        }
        else {
          names.keySet().retainAll(set);
        }
      }
      final Map<String, Class> result = names == null ? Collections.emptyMap() : names;
      return myChildrenMap = result;
    }

    private List<AbstractIntrospector> getContexts() {
      if (myContexts != null) {
        return myContexts;
      }
      final List<AbstractIntrospector> parents = new ArrayList<>();
      final AntDomMacroDef macroDef = myElement.getParentOfType(AntDomMacroDef.class, true);
      if (macroDef != null) {
        final AntDomSequentialTask body = macroDef.getMacroBody();
        if (body != null) {
          body.accept(new AntDomRecursiveVisitor() {
            @Override
            public void visitAntDomCustomElement(AntDomCustomElement custom) {
              if (myElement.equals(custom.getDeclaringElement())) {
                final AntDomElement parent = custom.getParentOfType(AntDomElement.class, true);
                if (parent != null) {
                  final Class type = parent.getChildDescription().getUserData(ELEMENT_IMPL_CLASS_KEY);
                  if (type != null) {
                    final AntIntrospector antIntrospector = AntIntrospector.getInstance(type);
                    if (antIntrospector != null) {
                      parents.add(new ClassIntrospectorAdapter(antIntrospector));
                    }
                  }
                }
              }
            }
          });
        }
      }
      return myContexts = parents;
    }
  }

  private static final class ScriptdefIntrospectorAdapter extends AbstractIntrospector {
    private final AntDomScriptDef myScriptDef;

    private ScriptdefIntrospectorAdapter(AntDomScriptDef scriptDef) {
      myScriptDef = scriptDef;
    }

    @Override
    public @NotNull Iterator<String> getAttributesIterator() {
      final List<AntDomScriptdefAttribute> macrodefAttributes = myScriptDef.getScriptdefAttributes();
      final List<String> attribs = new ArrayList<>(macrodefAttributes.size());
      for (AntDomScriptdefAttribute attribute : macrodefAttributes) {
        final String nameAttrib = attribute.getName().getRawText();
        if (nameAttrib != null) {
          attribs.add(nameAttrib);
        }
      }
      return attribs.iterator();
    }

    @Override
    public boolean isContainer() {
      return false;
    }
  }

  private static final class EnumerationToIteratorAdapter<T> implements Iterator<T> {
    private final Enumeration<? extends T> myEnum;

    EnumerationToIteratorAdapter(Enumeration<? extends T> enumeration) {
      myEnum = enumeration;
    }

    @Override
    public boolean hasNext() {
      return myEnum.hasMoreElements();
    }

    @Override
    public T next() {
      return myEnum.nextElement();
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("remove is not supported");
    }
  }
}
