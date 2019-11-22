// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jdom;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Interner;
import com.intellij.util.containers.OpenTHashSet;
import com.intellij.util.containers.StringInterner;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.util.JDOMUtil.getAttributes;

public final class JDOMInterner {
  @ApiStatus.Internal
  public static final JDOMInterner INSTANCE = new JDOMInterner();

  private static final Condition<Object> IS_ELEMENT = Conditions.instanceOf(Element.class);
  private final Interner<String> myStrings = new StringInterner();
  private final OpenTHashSet<Element> myElements = new OpenTHashSet<>(new TObjectHashingStrategy<Element>() {
    @Override
    public int computeHashCode(Element e) {
      int result = e.getName().hashCode() * 31;
      result += computeAttributesHashCode(e);
      List<Content> content = e.getContent();
      result = result * 31 + content.size();
      for (Content child : content) {
        if (child instanceof Text) {
          result = result * 31 + computeTextHashCode((Text)child);
        }
        else if (child instanceof Element) {
          result = result * 31 + computeHashCode((Element)child);
          break;
        }
      }
      return result;
    }

    @Override
    public boolean equals(Element o1, Element o2) {
      if (!Comparing.strEqual(o1.getName(), o2.getName())) return false;
      if (!attributesEqual(o1, o2)) return false;

      List<Content> content1 = o1.getContent();
      List<Content> content2 = o2.getContent();
      if (content1.size() != content2.size()) return false;
      for (int i = 0; i < content1.size(); i++) {
        Content c1 = content1.get(i);
        Content c2 = content2.get(i);
        if (c1 instanceof Text) {
          if (!(c2 instanceof Text)) return false;
          if (!Comparing.strEqual(c1.getValue(), c2.getValue())) return false;
        }
        else if (c1 instanceof Element) {
          if (!(c2 instanceof Element)) return false;
          if (!equals((Element)c1, (Element)c2)) return false;
        }
        else {
          throw new RuntimeException(c1.toString());
        }
      }
      return true;
    }
  });

  private static int computeAttributesHashCode(Element e) {
    List<Attribute> attributes = getAttributes(e);
    if (attributes instanceof ImmutableSameTypeAttributeList) {
      return attributes.hashCode();
    }
    int result = 1;
    for (Attribute attribute : attributes) {
      result = result * 31 + computeAttributeHashCode(attribute.getName(), attribute.getValue());
    }
    return result;
  }

  private static boolean attributesEqual(Element o1, Element o2) {
    if (o1 instanceof ImmutableElement)  {
      return ((ImmutableElement)o1).attributesEqual(o2);
    }
    if (o2 instanceof ImmutableElement)  {
      return ((ImmutableElement)o2).attributesEqual(o1);
    }
    List<Attribute> a1 = getAttributes(o1);
    List<Attribute> a2 = getAttributes(o2);
    if (a1.size() != a2.size()) return false;
    for (int i=0; i<a1.size(); i++) {
      Attribute attr1 = a1.get(i);
      Attribute attr2 = a2.get(i);
      if (!ImmutableElement.attributesEqual(attr1, attr2)) return false;
    }
    return true;
  }

  static int computeAttributeHashCode(String name, String value) {
    return name.hashCode() * 31 + (value == null ? 0 : value.hashCode());
  }

  private final OpenTHashSet<Text/*ImmutableText or ImmutableCDATA*/> myTexts = new OpenTHashSet<>(new TObjectHashingStrategy<Text>() {
    @Override
    public int computeHashCode(Text object) {
      return computeTextHashCode(object);
    }

    @Override
    public boolean equals(Text o1, Text o2) {
      return Comparing.strEqual(o1.getValue(), o2.getValue());
    }
  });

  private static int computeTextHashCode(Text object) {
    return object.getValue().hashCode();
  }

  @NotNull
  public synchronized Element internElement(@NotNull final Element element) {
    if (element instanceof ImmutableElement) return element;
    if (ContainerUtil.exists(element.getContent(), IS_ELEMENT)) {
      return new ImmutableElement(element, this);
    }
    Element interned = myElements.get(element);
    if (interned == null) {
      interned = new ImmutableElement(element, this);
      myElements.add(interned);
    }
    return interned;
  }

  public static boolean isInterned(@NotNull Element element) {
    return element instanceof ImmutableElement;
  }

  @NotNull
  synchronized Text internText(@NotNull Text text) {
    if (text instanceof ImmutableText || text instanceof ImmutableCDATA) return text;
    Text interned = myTexts.get(text);
    if (interned == null) {
      // no need to intern CDATA - there are no duplicates anyway
      interned = text instanceof CDATA ? new ImmutableCDATA(text.getText()) : new ImmutableText(myStrings.intern(text.getText()));
      myTexts.add(interned);
    }
    return interned;
  }

  synchronized String internString(String s) {
    return myStrings.intern(s);
  }
}
