/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import java.util.Collections;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 12/9/12
 * Time: 8:39 PM
 */
public class FilterFilePathStrings extends AbstractFilterChildren<String> {
  private final static FilterFilePathStrings ourInstance = new FilterFilePathStrings();

  public static FilterFilePathStrings getInstance() {
    return ourInstance;
  }

  @Override
  protected void sortAscending(List<String> list) {
    Collections.sort(list);
  }

  @Override
  protected boolean isAncestor(String parent, String child) {
    return child.startsWith(parent);
  }
}
