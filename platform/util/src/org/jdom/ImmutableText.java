// Copyright 2000-2017 JetBrains s.r.o.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package org.jdom;

import org.jetbrains.annotations.NotNull;

public class ImmutableText extends Text {
  ImmutableText(@NotNull String str) {
    super.setText(str);
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  @Override
  public Text clone() {
    Text text = new Text();
    text.value = value;
    return text;
  }

  @Override
  public Element getParent() {
    throw ImmutableElement.immutableError(this);
  }

  //////////////////////////////////////////////////////////////////////////
  @Override
  public Text setText(String str) {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  public void append(String str) {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  public void append(Text text) {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  public Content detach() {
    throw ImmutableElement.immutableError(this);
  }

  @Override
  protected Content setParent(Parent parent) {
    throw ImmutableElement.immutableError(this);
    //return null; // to be able to add this to the other element
  }
}
