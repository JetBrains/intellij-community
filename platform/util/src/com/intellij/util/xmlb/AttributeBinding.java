/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

public class AttributeBinding implements Binding {
  private final Accessor myAccessor;
  private final Attribute myAttribute;
  private Binding myBinding;

  public AttributeBinding(final Accessor accessor, final Attribute attribute) {
    myAccessor = accessor;
    myAttribute = attribute;
  }

  @Override
  public Object serialize(@NotNull Object o, Object context, SerializationFilter filter) {
    final Object v = myAccessor.read(o);
    final Object node = myBinding.serialize(v, context, filter);

    return new org.jdom.Attribute(myAttribute.value(), ((Content)node).getValue());
  }

  @Override
  @Nullable
  public Object deserialize(Object context, @NotNull Object... nodes) {
    assert nodes.length == 1;
    Object node = nodes[0];
    assert isBoundTo(node);

    org.jdom.Attribute attr = (org.jdom.Attribute)node;
    final String value = attr.getValue();
    final Text text = new Text(value);
    myAccessor.write(context, myBinding.deserialize(context, text));
    return context;
  }

  @Override
  public boolean isBoundTo(Object node) {
    return node instanceof org.jdom.Attribute && ((org.jdom.Attribute)node).getName().equals(myAttribute.value());
  }

  @Override
  public Class getBoundNodeType() {
    return org.jdom.Attribute.class;
  }

  @Override
  public void init() {
    myBinding = XmlSerializerImpl.getBinding(myAccessor);
    if (!Text.class.isAssignableFrom(myBinding.getBoundNodeType())) {
      throw new XmlSerializationException("Can't use attribute binding for non-text content: " + myAccessor);
    }
  }
}
