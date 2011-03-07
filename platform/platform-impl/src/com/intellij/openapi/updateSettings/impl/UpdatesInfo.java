/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.updateSettings.impl;


import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class UpdatesInfo {
  @NotNull private final List<Product> myProducts;

  public UpdatesInfo(Element element) {
    myProducts = new ArrayList<Product>();
    List children = element.getChildren();
    for (Object child : children) {
      myProducts.add(new Product((Element) child));
    }
  }

  @Nullable
  public Product getProduct(@NotNull String code){
    for (Product product : myProducts) {
      if (product.hasCode(code)){
        return product;
      }
    }
    return null;
  }

  public int getProductsCount() {
    return myProducts.size();
  }
}
