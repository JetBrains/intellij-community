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
package com.intellij.util;

/**
* Created by IntelliJ IDEA.
* User: Irina.Chernushina
* Date: 9/7/11
* Time: 3:15 PM
*/
public class Ticket {
  private int myId;

  public Ticket() {
    myId = 0;
  }

  public Ticket(int id) {
    myId = id;
  }

  public Ticket copy() {
    return new Ticket(myId);
  }

  public void increment() {
    ++ myId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Ticket ticket = (Ticket)o;

    if (myId != ticket.myId) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myId;
  }
}
