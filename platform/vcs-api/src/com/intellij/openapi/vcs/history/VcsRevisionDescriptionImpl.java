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
package com.intellij.openapi.vcs.history;

import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 8/2/11
 * Time: 12:46 AM
 * To change this template use File | Settings | File Templates.
 */
public class VcsRevisionDescriptionImpl implements VcsRevisionDescription {
  private final VcsRevisionNumber myNumber;
  private Date myDate;
  private String myAuthor;
  private String myMessage;

  public VcsRevisionDescriptionImpl(VcsRevisionNumber number, Date date, String author, String message) {
    myNumber = number;
    myDate = date;
    myAuthor = author;
    myMessage = message;
  }

  public VcsRevisionDescriptionImpl(VcsRevisionNumber number) {
    myNumber = number;
  }

  @Override
  public VcsRevisionNumber getRevisionNumber() {
    return myNumber;
  }

  @Override
  public Date getRevisionDate() {
    return myDate;
  }

  @Override
  public String getAuthor() {
    return myAuthor;
  }

  @Override
  public String getCommitMessage() {
    return myMessage;
  }

  public void setDate(Date date) {
    myDate = date;
  }

  public void setAuthor(String author) {
    myAuthor = author;
  }

  public void setMessage(String message) {
    myMessage = message;
  }
}
