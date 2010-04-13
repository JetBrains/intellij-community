/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.history.browser;

public class SHAHash {
  private final String myValue;

  public SHAHash(final String value) {
    myValue = value;
    assert myValue.length() == 40 : myValue;
  }

  public String getValue() {
    return myValue;
  }

  @Override
  public String toString() {
    return myValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SHAHash shaHash = (SHAHash)o;

    if (!myValue.equals(shaHash.myValue)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myValue.hashCode();
  }
}
