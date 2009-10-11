
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
package com.intellij.ui.plaf.beg;

import com.intellij.util.ui.UIUtil;

import java.awt.*;

public class BegTreeHandleUtil {
  public static void a(Graphics g, int i, int j, int k, int l) {
    g.translate(i, j);
    boolean flag = false;
    for(int i1 = 0; i1 < k; i1++){
      // beg: unknown start value for j1
      for(int j1 = 0; j1 < l; j1 += 2){
        UIUtil.drawLine(g, i1, j1, i1, j1);
      }
      flag = !flag;
    }
    g.translate(-i, -j);
  }

}
