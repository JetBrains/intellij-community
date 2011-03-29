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
package org.jetbrains.plugins.github;

/**
* @author oleg
*/
public class UnknownRepositoryInfo extends RepositoryInfo {
  private final String myId;
  private final String myName;
  private final String myOwner;

  public UnknownRepositoryInfo(final String id) {
    super(null);
    myId = id;
    myName = myId.substring(myId.lastIndexOf('/') + 1);
    myOwner = myId.substring(0, myId.lastIndexOf('/'));
  }

  public String getName() {
    return myName;
  }

  public String getOwner() {
    return myOwner;
  }

  public boolean isFork() {
    throw new UnsupportedOperationException("UnknownRepositoryInfo#isFork() shouldn't be called");
  }

  public String getParent() {
    throw new UnsupportedOperationException("UnknownRepositoryInfo#isFork() shouldn't be called");
  }

  public String getId() {
    return  myId;
  }
}
