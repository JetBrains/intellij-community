// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jdom;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.filter.ElementFilter;
import org.jdom.filter.Filter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class ImmutableElement extends Element {
  private static final List<Attribute> EMPTY_LIST = new ImmutableSameTypeAttributeList(ArrayUtil.EMPTY_STRING_ARRAY, null,
                                                                                       Namespace.NO_NAMESPACE);
  private final Content[] myContent;
  private static final Content[] EMPTY_CONTENT = new Content[0];
  private final List<Attribute> myAttributes;

  ImmutableElement(@NotNull Element origin, @NotNull final JDOMInterner interner) {
    content = null;
    name = interner.internString(origin.getName());
    myAttributes = internAttributes(origin, interner);

    List<Content> origContent = origin.getContent();
    if (origContent.isEmpty()) {
      myContent = EMPTY_CONTENT;
    }
    else {
      Content[] newContent = new Content[origContent.size()];
      int index = 0;
      for (Content o : origContent) {
        if (o instanceof Element) {
          Element newElement = interner.internElement((Element)o);
          newContent[index++]= newElement;
        }
        else if (o instanceof Text) {
          Text newText = interner.internText((Text)o);
          newContent[index++]= newText;
        }
        else if (o instanceof Comment) {
          // ignore
        }
        else {
          throw new RuntimeException(o.toString());
        }
      }

      // ContentList is final, can't subclass
      myContent = index == newContent.length ? newContent : Arrays.copyOf(newContent, index);
    }

    this.namespace = origin.getNamespace();
    for (Namespace addns : origin.getAdditionalNamespaces()) {
      super.addNamespaceDeclaration(addns);
    }
  }

  @NotNull
  private static List<Attribute> internAttributes(@NotNull Element origin, @NotNull final JDOMInterner interner) {
    List<Attribute> originAttributes = JDOMUtil.getAttributes(origin);
    if (originAttributes.isEmpty()) {
      return EMPTY_LIST;
    }

    AttributeType type = null;
    String[] nameValues = new String[originAttributes.size() * 2];
    Namespace namespace = null;
    for (int i = 0; i < originAttributes.size(); i++) {
      Attribute origAttribute = originAttributes.get(i);
      if (type == null) {
        type = origAttribute.getAttributeType();
        namespace = origAttribute.getNamespace();
      }
      else if (type != origAttribute.getAttributeType() || !origAttribute.getNamespace().equals(namespace)) {
        type = null;
        break; // no single type/namespace, fallback to ImmutableAttrList
      }
      String name = interner.internString(origAttribute.getName());
      String value = interner.internString(origAttribute.getValue());
      nameValues[i * 2] = name;
      nameValues[i * 2 + 1] = value;
    }

    if (type == null) {
      return Collections.unmodifiableList(ContainerUtil.map(originAttributes, new Function<Attribute, Attribute>() {
        @Override
        public Attribute fun(Attribute attribute) {
          return new ImmutableAttribute(interner.internString(attribute.getName()),
                                        interner.internString(attribute.getValue()),
                                        attribute.getAttributeType(), attribute.getNamespace());
        }
      }));
    }
    else {
      return new ImmutableSameTypeAttributeList(nameValues, type, namespace);
    }
  }

  @Override
  public int getContentSize() {
    return myContent.length;
  }

  @NotNull
  @Override
  public List<Content> getContent() {
    return Arrays.asList(myContent);
  }

  @Override
  public <T extends Content> List<T> getContent(final Filter<T> filter) {
    return (List<T>)ContainerUtil.filter(myContent, new Condition<Content>() {
      @Override
      public boolean value(Content content) {
        return filter.matches(content);
      }
    });
  }

  @Override
  public Content getContent(int index) {
    return myContent[index];
  }

  @Override
  public Iterator<Content> getDescendants() {
    throw immutableError(this);
  }

  @Override
  public <T extends Content> Iterator<T> getDescendants(Filter<T> filter) {
    throw immutableError(this);
  }

  @NotNull
  @Override
  public List<Element> getChildren() {
    return getContent(new ElementFilter());
  }

  @NotNull
  @Override
  public List<Element> getChildren(String name, Namespace ns) {
    return getContent(new ElementFilter(name, ns));
  }

  @Override
  public Element getChild(String name, Namespace ns) {
    List<Element> children = getChildren(name, ns);
    return children.isEmpty() ? null : children.get(0);
  }

  @Override
  public String getText() {
    if (myContent.length == 0) {
      return "";
    }

    // If we hold only a Text or CDATA, return it directly
    if (myContent.length == 1) {
      final Object obj = myContent[0];
      if (obj instanceof Text) {
        return ((Text)obj).getText();
      }
      else {
        return "";
      }
    }

    // Else build String up
    StringBuilder textContent = new StringBuilder();
    boolean hasText = false;

    for (Content content : myContent) {
      if (content instanceof Text) {
        textContent.append(((Text)content).getText());
        hasText = true;
      }
    }

    return hasText ? textContent.toString() : "";
  }

  @Override
  public int indexOf(final Content child) {
    return ArrayUtil.indexOf(myContent, child);
  }

  @Override
  public Namespace getNamespace(String prefix) {
    Namespace ns = super.getNamespace(prefix);
    if (ns == null) {
      for (Attribute a : myAttributes) {
        if (prefix.equals(a.getNamespacePrefix())) {
          return a.getNamespace();
        }
      }
    }
    return ns;
  }

  @Override
  public boolean hasAttributes() {
    return !myAttributes.isEmpty();
  }

  @Override
  public List<Attribute> getAttributes() {
    return myAttributes;
  }

  public int getAttributesSize() {
    return myAttributes.size();
  }

  @Override
  public Attribute getAttribute(String name, Namespace ns) {
    if (myAttributes instanceof ImmutableSameTypeAttributeList) {
      return ((ImmutableSameTypeAttributeList)myAttributes).get(name, ns);
    }
    String uri = namespace.getURI();
    for (int i = 0; i < myAttributes.size(); i++) {
      Attribute a = myAttributes.get(i);
      String oldURI = a.getNamespaceURI();
      String oldName = a.getName();
      if (oldURI.equals(uri) && oldName.equals(name)) {
        return a;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public String getAttributeValue(String attname) {
    return getAttributeValue(attname, Namespace.NO_NAMESPACE);
  }

  @Override
  public String getAttributeValue(String attname, String def) {
    return getAttributeValue(attname, Namespace.NO_NAMESPACE, def);
  }

  @Override
  public String getAttributeValue(String attname, Namespace ns) {
    return getAttributeValue(attname, ns, null);
  }

  @Override
  public String getAttributeValue(String name, Namespace ns, String def) {
    if (myAttributes instanceof ImmutableSameTypeAttributeList) {
      return ((ImmutableSameTypeAttributeList)myAttributes).getValue(name, ns, def);
    }
    Attribute attribute = getAttribute(name, ns);
    return attribute == null ? def : attribute.getValue();
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public Element clone() {
    final Element element = new Element();

    element.content = new ContentList(element);
    element.attributes = new AttributeList(element);
    element.name = getName();
    element.namespace = getNamespace();

    // Cloning attributes
    List<Attribute> attributes = getAttributes();
    if (attributes != null) {
      for (final Attribute attribute : attributes) {
        element.attributes.add(attribute.clone());
      }
    }

    // Cloning additional namespaces
    if (additionalNamespaces != null) {
      element.additionalNamespaces = new ArrayList<Namespace>(additionalNamespaces);
    }

    // Cloning content
    List<Content> content = getContent();
    for (Content c : content) {
      element.content.add(c.clone());
    }

    return element;
  }

  @Override
  public Element getParent() {
    throw immutableError(this);
  }

  public boolean attributesEqual(Element element) {
    List<Attribute> attrs = element.getAttributes();
    if (myAttributes instanceof ImmutableSameTypeAttributeList) {
      return myAttributes.equals(attrs);
    }

    if (myAttributes.size() != attrs.size()) return false;
    for (int i = 0; i < myAttributes.size(); i++) {
      Attribute attribute = myAttributes.get(i);
      Attribute oAttr = attrs.get(i);
      if (!attributesEqual(attribute, oAttr)) return false;
    }
    return true;
  }

  public static boolean attributesEqual(Attribute a1, Attribute a2) {
    return a1.getName().equals(a2.getName()) &&
           Comparing.equal(a1.getValue(), a2.getValue()) &&
           a1.getAttributeType() == a2.getAttributeType() &&
           a1.getNamespace().equals(a2.getNamespace());
  }

  @NotNull
  static IncorrectOperationException immutableError(Object element) {
    return new IncorrectOperationException("Can't change immutable element: " +
                                           element.getClass() + ". To obtain mutable Element call .clone()");
  }

  //////////////////////////////////////////////////////////////////////
  @Override
  public Element detach() {
    throw immutableError(this);
  }

  @Override
  public Element setName(String name) {
    throw immutableError(this);
  }

  @Override
  public Element setNamespace(Namespace namespace) {
    throw immutableError(this);
  }

  @Override
  public void addNamespaceDeclaration(Namespace additionalNamespace) {
    throw immutableError(this);
  }

  @Override
  public void removeNamespaceDeclaration(Namespace additionalNamespace) {
    throw immutableError(this);
  }

  @Override
  public Element setText(String text) {
    throw immutableError(this);
  }

  @Override
  public List<Content> removeContent() {
    throw immutableError(this);
  }

  @Override
  public <T extends Content> List<T> removeContent(Filter<T> filter) {
    throw immutableError(this);
  }

  @Override
  public Element setContent(Collection<? extends Content> newContent) {
    throw immutableError(this);
  }

  @Override
  public Element setContent(int index, Content child) {
    throw immutableError(this);
  }

  @Override
  public Parent setContent(int index, Collection<? extends Content> newContent) {
    throw immutableError(this);
  }

  @Override
  public Element addContent(String str) {
    throw immutableError(this);
  }

  @Override
  public Element addContent(Content child) {
    throw immutableError(this);
  }

  @Override
  public Element addContent(Element child) {
    throw immutableError(this);
  }

  @Override
  public Element addContent(Collection<? extends Content> newContent) {
    throw immutableError(this);
  }

  @Override
  public Element addContent(int index, Content child) {
    throw immutableError(this);
  }

  @Override
  public Element addContent(int index, Collection<? extends Content> newContent) {
    throw immutableError(this);
  }

  @Override
  public boolean removeContent(Content child) {
    throw immutableError(this);
  }

  @Override
  public Content removeContent(int index) {
    throw immutableError(this);
  }

  @Override
  public Element setContent(Content child) {
    throw immutableError(this);
  }

  @Override
  public Element setAttributes(Collection newAttributes) {
    throw immutableError(this);
  }

  @Override
  public Element setAttributes(List newAttributes) {
    throw immutableError(this);
  }

  @Override
  public Element setAttribute(@NotNull String name, @NotNull String value) {
    throw immutableError(this);
  }

  @Override
  public Element setAttribute(@NotNull String name, @NotNull String value, Namespace ns) {
    throw immutableError(this);
  }

  @Override
  public Element setAttribute(@NotNull Attribute attribute) {
    throw immutableError(this);
  }

  @Override
  public boolean removeAttribute(String name) {
    throw immutableError(this);
  }

  @Override
  public boolean removeAttribute(String name, Namespace ns) {
    throw immutableError(this);
  }

  @Override
  public boolean removeAttribute(Attribute attribute) {
    throw immutableError(this);
  }

  @Override
  public boolean removeChild(String name) {
    throw immutableError(this);
  }

  @Override
  public boolean removeChild(String name, Namespace ns) {
    throw immutableError(this);
  }

  @Override
  public boolean removeChildren(String name) {
    throw immutableError(this);
  }

  @Override
  public boolean removeChildren(String name, Namespace ns) {
    throw immutableError(this);
  }

  @Override
  protected Content setParent(Parent parent) {
    throw immutableError(this);
    //return null; // to be able to add this element to other's content
  }
}
