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

public enum TextMediaType
{
  CALENDAR("text/calendar"),
  CSS("text/css"),
  CSV("text/csv"),
  DIRECTORY("text/directory"),
  DNS("text/dns"),
  ECMASCRIPT("text/ecmascript"),
  ENRICHED("text/enriched"),
  EXAMPLE("text/example"),
  HTML("text/html"),
  JAVASCRIPT("text/javascript"),
  PARITYFEC("text/parityfec"),
  PLAIN("text/plain"),
  PRS_FALLENSTEIN_RST("text/prs.fallenstein.rst"),
  PRS_LINES_TAG("text/prs.lines.tag"),
  RED("text/RED"),
  RFC822_HEADERS("text/rfc822-headers"),
  RICHTEXT("text/richtext"),
  RTF("text/rtf"),
  RTX("text/rtx"),
  SGML("text/sgml"),
  T140("text/t140"),
  TAB_SEPARATED_VALUES("text/tab-separated-values"),
  TROFF("text/troff"),
  URI_LIST("text/uri-list"),
  VND_ABC("text/vnd.abc"),
  VND_CURL("text/vnd.curl"),
  VND_DMCLIENTSCRIPT("text/vnd.DMClientScript"),
  VND_ESMERTEC_THEME_DESCRIPTOR("text/vnd.esmertec.theme-descriptor"),
  VND_FLY("text/vnd.fly"),
  VND_FMI_FLEXSTOR("text/vnd.fmi.flexstor"),
  VND_IN3D_3DML("text/vnd.in3d.3dml"),
  VND_IN3D_SPOT("text/vnd.in3d.spot"),
  VND_IPTC_NEWSML("text/vnd.IPTC.NewsML"),
  VND_IPTC_NITF("text/vnd.IPTC.NITF"),
  VND_LATEX_Z("text/vnd.latex-z"),
  VND_MOTOROLA_REFLEX("text/vnd.motorola.reflex"),
  VND_MS_MEDIAPACKAGE("text/vnd.ms-mediapackage"),
  VND_NET2PHONE_COMMCENTER_COMMAND("text/vnd.net2phone.commcenter.command"),
  VND_SUN_J2ME_APP_DESCRIPTOR("text/vnd.sun.j2me.app-descriptor"),
  VND_TROLLTECH_LINGUIST("text/vnd.trolltech.linguist"),
  VND_WAP_SI("text/vnd.wap.si"),
  VND_WAP_SL("text/vnd.wap.sl"),
  VND_WAP_WML("text/vnd.wap.wml"),
  VND_WAP_WMLSCRIPT("text/vnd.wap.wmlscript"),
  XML("text/xml"),
  XML_EXTERNAL_PARSED_ENTITY("text/xml-external-parsed-entity");

  private final String contentType;

  TextMediaType(String contentType)
  {
    this.contentType = contentType;
  }

  public String toString()
  {
    return contentType;
  }
}