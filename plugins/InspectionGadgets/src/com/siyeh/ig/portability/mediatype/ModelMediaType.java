/*
 * Copyright 2006 Mark Scott
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
package com.siyeh.ig.portability.mediatype;

public enum ModelMediaType
{
  EXAMPLE("model/example"),
  IGES("model/iges"),
  MESH("model/mesh"),
  VND_DWF("model/vnd.dwf"),
  VND_FLATLAND_3DML("model/vnd.flatland.3dml"),
  VND_GDL("model/vnd.gdl"),
  VND_GS_GDL("model/vnd.gs-gdl"),
  VND_GTW("model/vnd.gtw"),
  VND_MOML_XML("model/vnd.moml+xml"),
  VND_MTS("model/vnd.mts"),
  VND_PARASOLID_TRANSMIT_BINARY("model/vnd.parasolid.transmit.binary"),
  VND_PARASOLID_TRANSMIT_TEXT("model/vnd.parasolid.transmit.text"),
  VND_VTU("model/vnd.vtu"),
  VRML("model/vrml");

  private final String contentType;

  ModelMediaType(String contentType)
  {
    this.contentType = contentType;
  }

  public String toString()
  {
    return contentType;
  }
}