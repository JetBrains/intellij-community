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

import org.jdom.Content;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TextBinding implements Binding {
  private final Accessor myAccessor;
  private volatile Binding myBinding;

  public TextBinding(final Accessor accessor) {
    myAccessor = accessor;
  }

  public Object serialize(Object o, Object context, SerializationFilter filter) {
    final Object v = myAccessor.read(o);
    final Object node = myBinding.serialize(v, context, filter);

    return new Text(((Content)node).getValue());
  }

  @Nullable
  public Object deserialize(Object context, @NotNull Object... nodes) {
    assert nodes.length == 1;
    Object node = nodes[0];
    assert isBoundTo(node);

    myAccessor.write(context, myBinding.deserialize(context, nodes[0]));
    return context;
  }

  public boolean isBoundTo(Object node) {
    return node instanceof Text;
  }

  public Class getBoundNodeType() {
    return Text.class;
  }

  public void init() {
    myBinding = XmlSerializerImpl.getBinding(myAccessor);
    if (!Text.class.isAssignableFrom(myBinding.getBoundNodeType())) {
      throw new XmlSerializationException("Can't use attribute binding for non-text content: " + myAccessor);
    }
  }
}
