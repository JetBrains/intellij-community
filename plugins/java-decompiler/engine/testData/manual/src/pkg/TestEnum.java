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
package pkg;

public enum TestEnum {
  E1,
  E2() {
    @Override
    public void m() { }
  },
  E3("-", Type.ODD),
  E4("+", Type.EVEN) {
    @Override
    public void m() { }
  };

  private enum Type {ODD, EVEN}

  public void m() { }

  private String s;

  private TestEnum() { this("?", null); }
  private TestEnum(@Deprecated String s, Type t) { this.s = s; }
}
