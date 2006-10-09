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
package com.siyeh.ig.portability.contenttype;

public enum ImageMediaType
{
  CGM("image/cgm"),
  EXAMPLE("image/example"),
  FITS("image/fits"),
  G3FAX("image/g3fax"),
  GIF("image/gif"),
  IEF("image/ief"),
  JP2("image/jp2"),
  JPEG("image/jpeg"),
  JPM("image/jpm"),
  JPX("image/jpx"),
  NAPLPS("image/naplps"),
  PNG("image/png"),
  PRS_BTIF("image/prs.btif"),
  PRS_PTI("image/prs.pti"),
  T38("image/t38"),
  TIFF("image/tiff"),
  TIFF_FX("image/tiff-fx"),
  VND_ADOBE_PHOTOSHOP("image/vnd.adobe.photoshop"),
  VND_CNS_INF2("image/vnd.cns.inf2"),
  VND_DJVU("image/vnd.djvu"),
  VND_DWG("image/vnd.dwg"),
  VND_DXF("image/vnd.dxf"),
  VND_FASTBIDSHEET("image/vnd.fastbidsheet"),
  VND_FPX("image/vnd.fpx"),
  VND_FST("image/vnd.fst"),
  VND_FUJIXEROX_EDMICS_MMR("image/vnd.fujixerox.edmics-mmr"),
  VND_FUJIXEROX_EDMICS_RLC("image/vnd.fujixerox.edmics-rlc"),
  VND_GLOBALGRAPHICS_PGB("image/vnd.globalgraphics.pgb"),
  VND_MICROSOFT_ICON("image/vnd.microsoft.icon"),
  VND_MIX("image/vnd.mix"),
  VND_MS_MODI("image/vnd.ms-modi"),
  VND_NET_FPX("image/vnd.net-fpx"),
  VND_SEALED_PNG("image/vnd.sealed.png"),
  VND_SEALEDMEDIA_SOFTSEAL_GIF("image/vnd.sealedmedia.softseal.gif"),
  VND_SEALEDMEDIA_SOFTSEAL_JPG("image/vnd.sealedmedia.softseal.jpg"),
  VND_SVF("image/vnd.svf"),
  VND_WAP_WBMP("image/vnd.wap.wbmp"),
  VND_XIFF("image/vnd.xiff");

  private final String contentType;

  ImageMediaType(String contentType)
  {
    this.contentType = contentType;
  }

  public String toString()
  {
    return contentType;
  }
}