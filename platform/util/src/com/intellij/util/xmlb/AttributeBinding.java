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

package com.intellij.util.xmlb;

import com.intellij.util.xmlb.annotations.Attribute;
import org.jdom.Content;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AttributeBinding extends BasePrimitiveBinding {
  public AttributeBinding(@NotNull Accessor accessor, @NotNull Attribute attribute) {
    super(accessor, attribute.value(), attribute.converter());
  }

  @Override
  public Object serialize(@NotNull Object o, Object context, SerializationFilter filter) {
    Object value = myAccessor.read(o);
    if (value == null) {
      return context;
    }

    String stringValue;
    if (myConverter != null) {
      stringValue = myConverter.toString(value);
    }
    else {
      assert myBinding != null;
      stringValue = ((Content)myBinding.serialize(value, context, filter)).getValue();
    }
    return new org.jdom.Attribute(myName, stringValue);
  }

  @Override
  @Nullable
  public Object deserialize(Object context, @NotNull Object... nodes) {
    assert nodes.length == 1;
    org.jdom.Attribute node = (org.jdom.Attribute)nodes[0];
    assert isBoundTo(node);
    Object value;
    if (myConverter != null) {
      value = myConverter.fromString(node.getValue());
    }
    else {
      assert myBinding != null;
      value = myBinding.deserialize(context, new Text(node.getValue()));
    }
    myAccessor.write(context, value);
    return context;
  }

  @Override
  public boolean isBoundTo(Object node) {
    return node instanceof org.jdom.Attribute && ((org.jdom.Attribute)node).getName().equals(myName);
  }

  @Override
  public Class getBoundNodeType() {
    return org.jdom.Attribute.class;
  }

  @Override
  public void init() {
    super.init();
    if (myBinding != null && !Text.class.isAssignableFrom(myBinding.getBoundNodeType())) {
      throw new XmlSerializationException("Can't use attribute binding for non-text content: " + myAccessor);
    }
  }
}
