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
package com.intellij.lang.ant.dom;

import com.intellij.util.xml.SubTagList;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 8, 2010
 */
public abstract class AntDomAntlib extends AntDomElement{

  @SubTagList("typedef")
  public abstract List<AntDomTypeDef> getTypedefs();

  @SubTagList("taskdef")
  public abstract List<AntDomTypeDef> getTaskdefs();

  @SubTagList("macrodef")
  public abstract List<AntDomTypeDef> getMacrodefs();

  @SubTagList("presetdef")
  public abstract List<AntDomTypeDef> getPresetdefs();

  @SubTagList("scriptdef")
  public abstract List<AntDomTypeDef> getScriptdefs();
}
