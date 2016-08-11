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
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.AntIntrospector;
import com.intellij.lang.ant.ReflectedProject;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.PomTarget;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.*;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Reference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 9, 2010
 */
public class AntDomExtender extends DomExtender<AntDomElement>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.dom.AntDomExtender");
  
  private static final Key<Class> ELEMENT_IMPL_CLASS_KEY = Key.create("_element_impl_class_");
  private static final Key<Boolean> IS_TASK_CONTAINER = Key.create("_task_container_");
  private static final Map<String, Class<? extends AntDomElement>> TAG_MAPPING = new HashMap<>();
  static {
    TAG_MAPPING.put("property", AntDomProperty.class);
    TAG_MAPPING.put("dirname", AntDomDirname.class);
    TAG_MAPPING.put("fileset", AntDomFileSet.class);
    TAG_MAPPING.put("dirset", AntDomDirSet.class);
    TAG_MAPPING.put("filelist", AntDomFileList.class);
    TAG_MAPPING.put("pathelement", AntDomPathElement.class);
    TAG_MAPPING.put("path", AntDomPath.class);
    TAG_MAPPING.put("classpath", AntDomClasspath.class);
    TAG_MAPPING.put("typedef", AntDomTypeDef.class);
    TAG_MAPPING.put("taskdef", AntDomTaskdef.class);
    TAG_MAPPING.put("presetdef", AntDomPresetDef.class);
    TAG_MAPPING.put("macrodef", AntDomMacroDef.class);
    TAG_MAPPING.put("scriptdef", AntDomScriptDef.class);
    TAG_MAPPING.put("antlib", AntDomAntlib.class);
    TAG_MAPPING.put("ant", AntDomAnt.class);
    TAG_MAPPING.put("antcall", AntDomAntCall.class);
    TAG_MAPPING.put("available", AntDomPropertyDefiningTaskWithDefaultValue.class);
    TAG_MAPPING.put("condition", AntDomPropertyDefiningTaskWithDefaultValue.class);
    TAG_MAPPING.put("uptodate", AntDomPropertyDefiningTaskWithDefaultValue.class);
    TAG_MAPPING.put("checksum", AntDomChecksumTask.class);
    TAG_MAPPING.put("loadfile", AntDomLoadFileTask.class);
    TAG_MAPPING.put("whichresource", AntDomWhichResourceTask.class);
    TAG_MAPPING.put("jarlib-resolve", AntDomPropertyDefiningTask.class);
    TAG_MAPPING.put("p4counter", AntDomPropertyDefiningTask.class);
    TAG_MAPPING.put("pathconvert", AntDomPropertyDefiningTask.class);
    TAG_MAPPING.put("basename", AntDomBasenameTask.class);
    TAG_MAPPING.put("length", AntDomLengthTask.class);
    TAG_MAPPING.put("tempfile", AntDomTempFile.class);
    TAG_MAPPING.put("exec", AntDomExecTask.class);
    TAG_MAPPING.put("buildnumber", AntDomBuildnumberTask.class);
    TAG_MAPPING.put("tstamp", AntDomTimestampTask.class);
    TAG_MAPPING.put("format", AntDomTimestampTaskFormat.class);
    TAG_MAPPING.put("input", AntDomInputTask.class);
  }

  public void registerExtensions(@NotNull final AntDomElement antDomElement, @NotNull DomExtensionsRegistrar registrar) {
    final XmlElement xmlElement = antDomElement.getXmlElement();
    if (xmlElement instanceof XmlTag) {
      final XmlTag xmlTag = (XmlTag)xmlElement;
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
      final Hashtable<String,Class> coreTaskDefs = reflected.getTaskDefinitions();
      final Hashtable<String, Class> coreTypeDefs = reflected.getDataTypeDefinitions();
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
            if (coreTaskDefs != null){
              elemType = coreTaskDefs.get(tagName);
            }
          }

          if (elemType == null) {
            if (coreTypeDefs != null){
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
        parentIntrospector = new ClassIntrospectorAdapter(classBasedIntrospector, coreTaskDefs, coreTypeDefs);
      }
      else {
        if (isCustom) {
          final AntDomNamedElement declaringElement = ((AntDomCustomElement)antDomElement).getDeclaringElement();
          if (declaringElement instanceof AntDomMacroDef) {
            parentIntrospector = new MacrodefIntrospectorAdapter((AntDomMacroDef)declaringElement);
          }
          else if (declaringElement instanceof AntDomMacrodefElement){
            parentIntrospector = new MacrodefElementOccurrenceIntrospectorAdapter((AntDomMacrodefElement)declaringElement)/*ContainerElementIntrospector.INSTANCE*/;
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
            for (Map.Entry<String, Class> entry : coreTaskDefs.entrySet()) {
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
            for (Map.Entry<String, Class> entry : coreTypeDefs.entrySet()) {
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
          registrar.registerCustomChildrenExtension(AntDomCustomElement.class, new AntCustomTagNameDescriptor());
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
                if (coreTypeDefs != null){
                  type = coreTypeDefs.get(nestedElementName);
                }
              }
              if (type != null) {
                extension.putUserData(ELEMENT_IMPL_CLASS_KEY, type);
              }
              AntDomElement.Role role = AntDomElement.Role.DATA_TYPE;
              if (coreTaskDefs != null && coreTaskDefs.containsKey(nestedElementName)) {
                role = AntDomElement.Role.TASK;
              }
              else if (type != null && isAssignableFrom(Task.class.getName(), type)) {
                role = AntDomElement.Role.TASK;
              }
              if (role != null) {
                extension.putUserData(AntDomElement.ROLE, role);
              }
            }
          }
          registrar.registerCustomChildrenExtension(AntDomCustomElement.class, new AntCustomTagNameDescriptor());
        }
      }
    }
  }

  private static void defineAttributes(XmlTag xmlTag, DomExtensionsRegistrar registrar, DomGenericInfo genericInfo, AbstractIntrospector parentIntrospector) {
    final Map<String, Pair<Type, Class>> registeredAttribs = getStaticallyRegisteredAttributes(genericInfo);
    // define attributes discovered by introspector and not yet defined statically
    final Iterator<String> introspectedAttributes = parentIntrospector.getAttributesIterator();
    while (introspectedAttributes.hasNext()) {
      final String attribName = introspectedAttributes.next();
      if (genericInfo.getAttributeChildDescription(attribName) == null) { // if not defined yet 
        final String _attribName = attribName.toLowerCase(Locale.US);
        final Pair<Type, Class> types = registeredAttribs.get(_attribName);
        Type type = types != null? types.getFirst() : null;
        Class converterClass = types != null ? types.getSecond() : null;
        if (type == null) {
          type = String.class; // use String by default
          final Class attributeType = parentIntrospector.getAttributeType(attribName);
          if (attributeType != null) {
            // handle well-known types
            if (File.class.isAssignableFrom(attributeType)) {
              type = PsiFileSystemItem.class;
              converterClass = AntPathConverter.class;
            }
            else if (Boolean.class.isAssignableFrom(attributeType)){
              type = Boolean.class;
              converterClass = AntBooleanConverter.class;
            }
            else if (isAssignableFrom(Reference.class.getName(), attributeType)) {
              converterClass = AntDomRefIdConverter.class;
            }
          }
        }
        
        LOG.assertTrue(type != null);
        
        registerAttribute(registrar, attribName, type, converterClass);
        if (types == null) { // augment the map if this was a newly added attribute
          registeredAttribs.put(_attribName, Pair.create(type, converterClass));
        }
      }
    }
    // handle attribute case problems: 
    // additionaly register all attributes that exist in XML but differ from the registered ones only in case
    for (XmlAttribute xmlAttribute : xmlTag.getAttributes()) {
      final String existingAttribName = xmlAttribute.getName();
      if (genericInfo.getAttributeChildDescription(existingAttribName) == null) {
        final Pair<Type, Class> pair = registeredAttribs.get(existingAttribName.toLowerCase(Locale.US));
        if (pair != null) { // if such attribute should actually be here
          registerAttribute(registrar, existingAttribName, pair.getFirst(), pair.getSecond());
        }
      }
    }
  }

  private static void registerAttribute(DomExtensionsRegistrar registrar, String attribName, final @NotNull Type attributeType, final @Nullable Class converterType) {
    final DomExtension extension = registrar.registerGenericAttributeValueChildExtension(new XmlName(attribName), attributeType);
    if (converterType != null) {
      try {
        extension.setConverter((Converter)converterType.newInstance());
      }
      catch (InstantiationException e) {
        LOG.info(e);
      }
      catch (IllegalAccessException e) {
        LOG.info(e);
      }
    }
  }

  private static Map<String, Pair<Type, Class>> getStaticallyRegisteredAttributes(final DomGenericInfo genericInfo) {
    final Map<String, Pair<Type, Class>> map = new HashMap<>();
    for (DomAttributeChildDescription description : genericInfo.getAttributeChildrenDescriptions()) {
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
          map.put(name.toLowerCase(Locale.US), new Pair<>(attribType, converterType));
        }
      }
    }
    return map;
  }

  @Nullable
  private static DomExtension registerChild(DomExtensionsRegistrar registrar, DomGenericInfo elementInfo, String childName) {
    if (elementInfo.getCollectionChildDescription(childName) == null) { // register if not yet defined statically
      Class<? extends AntDomElement> modelClass = getModelClass(childName);
      if (modelClass == null) {
        modelClass = AntDomElement.class;
      }
      return registrar.registerCollectionChildrenExtension(new XmlName(childName), modelClass);
    }
    return null;
  }

  @Nullable
  public static AntIntrospector getIntrospector(Class c) {
    try {
      return AntIntrospector.getInstance(c);
    }
    catch (Throwable ignored) {
    }
    return null;
  }

  @Nullable
  private static Class<? extends AntDomElement> getModelClass(@NotNull String tagName) {
    return TAG_MAPPING.get(tagName.toLowerCase(Locale.US));
  }

  private static boolean isAssignableFrom(final String baseClassName, final Class clazz) {
    try {
      final ClassLoader loader = clazz.getClassLoader();
      if (loader != null) {
        final Class baseClass = loader.loadClass(baseClassName);
        return baseClass.isAssignableFrom(clazz);
      }
    }
    catch (ClassNotFoundException ignored) {
    }
    return false;
  }

  private static class AntCustomTagNameDescriptor extends CustomDomChildrenDescription.TagNameDescriptor {

    public Set<EvaluatedXmlName> getCompletionVariants(@NotNull DomElement parent) {
      if (!(parent instanceof AntDomElement)) {
        return Collections.emptySet();
      }
      final AntDomElement element = (AntDomElement)parent;
      final AntDomProject antDomProject = element.getAntProject();
      if (antDomProject == null) {
        return Collections.emptySet();
      }
      final CustomAntElementsRegistry registry = CustomAntElementsRegistry.getInstance(antDomProject);
      final Set<EvaluatedXmlName> result = new HashSet<>();
      for (XmlName variant : registry.getCompletionVariants(element)) {
        final String ns = variant.getNamespaceKey();
        result.add(new DummyEvaluatedXmlName(variant, ns != null? ns : ""));
      }
      return result;
    }

    @Nullable
    public PomTarget findDeclaration(DomElement parent, @NotNull EvaluatedXmlName name) {
      final XmlName xmlName = name.getXmlName();
      return doFindDeclaration(parent, xmlName);
    }

    @Nullable
    public PomTarget findDeclaration(@NotNull DomElement child) {
      XmlName name = new XmlName(child.getXmlElementName(), child.getXmlElementNamespace());
      return doFindDeclaration(child.getParent(), name);
    }

    @Nullable
    private static PomTarget doFindDeclaration(DomElement parent, XmlName xmlName) {
      if (!(parent instanceof AntDomElement)) {
        return null;
      }
      final AntDomElement parentElement = (AntDomElement)parent;
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
      if (target == null && declaringElement instanceof AntDomTypeDef) {
        final AntDomTypeDef typedef = (AntDomTypeDef)declaringElement;
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
  
  private static abstract class AbstractIntrospector {
    @NotNull
    public Iterator<String> getAttributesIterator() {
      return Collections.<String>emptyList().iterator();
    }
    
    @NotNull
    public Iterator<String> getNestedElementsIterator(){
      return Collections.<String>emptyList().iterator();
    }

    public abstract boolean isContainer();

    @Nullable
    public Class getAttributeType(String attribName) {
      return null;
    }

    @Nullable
    public Class getNestedElementType(String elementName) {
      return null;
    }
  }
  
  private static class ClassIntrospectorAdapter extends AbstractIntrospector {

    private final AntIntrospector myIntrospector;
    private final Map<String, Class> myCoreTaskDefs;
    private final Map<String, Class> myCoreTypeDefs;
    private List<String> myNestedElements;
    private Map<String, Class> myNestedElementTypes;

    private ClassIntrospectorAdapter(AntIntrospector introspector) {
      this(introspector, null, null);
    }

    public ClassIntrospectorAdapter(AntIntrospector introspector, Map<String, Class> coreTaskDefs, Map<String, Class> coreTypeDefs) {
      myIntrospector = introspector;
      myCoreTaskDefs = coreTaskDefs != null? coreTaskDefs : Collections.<String, Class>emptyMap();
      myCoreTypeDefs = coreTypeDefs != null? coreTypeDefs : Collections.<String, Class>emptyMap();
    }

    @NotNull 
    public Iterator<String> getAttributesIterator() {
      return new EnumerationToIteratorAdapter<>(myIntrospector.getAttributes());
    }

    public Class getAttributeType(String attribName) {
      return myIntrospector.getAttributeType(attribName);
    }

    public boolean isContainer() {
      return myIntrospector.isContainer();
    }

    @NotNull 
    public Iterator<String> getNestedElementsIterator() {
      initNestedElements();
      return myNestedElements.iterator();
    }

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
      }
    }

    private void processEntries(String extPoint, final Map<String, Class> definitions) {
      for (Map.Entry<String, Class> entry : definitions.entrySet()) {
        final String elementName = entry.getKey();
        final Class taskClass = entry.getValue();
        if (isAssignableFrom(extPoint, taskClass)) {
          myNestedElements.add(elementName);
          myNestedElementTypes.put(elementName, taskClass);
        }
      }
    }
  }
  
  private static class MacrodefIntrospectorAdapter extends AbstractIntrospector {
    private final AntDomMacroDef myMacrodef;

    private MacrodefIntrospectorAdapter(AntDomMacroDef macrodef) {
      myMacrodef = macrodef;
    }

    @NotNull 
    public Iterator<String> getAttributesIterator() {
      final List<AntDomMacrodefAttribute> macrodefAttributes = myMacrodef.getMacroAttributes();
      if (macrodefAttributes.size() == 0) {
        return Collections.<String>emptyList().iterator();
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

    public boolean isContainer() {
      for (AntDomMacrodefElement element : myMacrodef.getMacroElements()) {
        final GenericAttributeValue<Boolean> implicit = element.isImplicit();
        if (implicit != null && Boolean.TRUE.equals(implicit.getValue())) {
          return true;
        }
      }
      return false;
    }
  }

  private static class MacrodefElementOccurrenceIntrospectorAdapter extends AbstractIntrospector {
    private final AntDomMacrodefElement myElement;
    private volatile List<AbstractIntrospector> myContexts;
    private volatile Map<String, Class> myChildrenMap;

    private MacrodefElementOccurrenceIntrospectorAdapter(AntDomMacrodefElement element) {
      myElement = element;
    }

    public boolean isContainer() {
      final List<AbstractIntrospector> contexts = getContexts();
      for (AbstractIntrospector context : contexts) {
        if (!context.isContainer()) {
          return false;
        }
      }
      return true;
    }

    @NotNull
    public Iterator<String> getNestedElementsIterator() {
      return getNestedElementsMap().keySet().iterator();
    }

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
        for (Iterator<String> it = context.getNestedElementsIterator();it.hasNext();) {
          final String name = it.next();
          set.add(name);
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
      final Map<String, Class> result = names == null ? Collections.<String, Class>emptyMap() : names;
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

  private static class ScriptdefIntrospectorAdapter extends AbstractIntrospector {
    private final AntDomScriptDef myScriptDef;

    private ScriptdefIntrospectorAdapter(AntDomScriptDef scriptDef) {
      myScriptDef = scriptDef;
    }
    
    @NotNull 
    public Iterator<String> getAttributesIterator() {
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
    
    public boolean isContainer() {
      return false;
    }
  }
  
  private static class ContainerElementIntrospector extends AbstractIntrospector{
    public static final ContainerElementIntrospector INSTANCE = new ContainerElementIntrospector();
    public boolean isContainer() {
      return true;
    }
  }
  
  private static class EnumerationToIteratorAdapter<T> implements Iterator<T> {

    private final Enumeration<T> myEnum;

    public EnumerationToIteratorAdapter(Enumeration<T> enumeration) {
      myEnum = enumeration;
    }

    public boolean hasNext() {
      return myEnum.hasMoreElements();
    }

    public T next() {
      return myEnum.nextElement();
    }

    public void remove() {
      throw new UnsupportedOperationException("remove is not supported");
    }
  }
  
}
