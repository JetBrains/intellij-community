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

public enum VideoMediaType
{
  THREE_GPP("video/3gpp"),
  THREE_GPP2("video/3gpp2"),
  THREE_GPP_TT("video/3gpp-tt"),
  BMPEG("video/BMPEG"),
  BT656("video/BT656"),
  CELB("video/CelB"),
  DV("video/DV"),
  EXAMPLE("video/example"),
  H261("video/H261"),
  H263("video/H263"),
  H263_1998("video/H263-1998"),
  H263_2000("video/H263-2000"),
  H264("video/H264"),
  JPEG("video/JPEG"),
  MJ2("video/MJ2"),
  MP1S("video/MP1S"),
  MP2P("video/MP2P"),
  MP2T("video/MP2T"),
  MP4("video/mp4"),
  MP4V_ES("video/MP4V-ES"),
  MPV("video/MPV"),
  MPEG("video/mpeg"),
  MPEG4_GENERIC("video/mpeg4-generic"),
  NV("video/nv"),
  PARITYFEC("video/parityfec"),
  POINTER("video/pointer"),
  QUICKTIME("video/quicktime"),
  RAW("video/raw"),
  RTX("video/rtx"),
  SMPTE292M("video/SMPTE292M"),
  VC1("video/vc1"),
  VND_DLNA_MPEG_TTS("video/vnd.dlna.mpeg-tts"),
  VND_FVT("video/vnd.fvt"),
  VND_HNS_VIDEO("video/vnd.hns.video"),
  VND_MOTOROLA_VIDEO("video/vnd.motorola.video"),
  VND_MOTOROLA_VIDEOP("video/vnd.motorola.videop"),
  VND_MPEGURL("video/vnd.mpegurl"),
  VND_NOKIA_INTERLEAVED_MULTIMEDIA("video/vnd.nokia.interleaved-multimedia"),
  VND_NOKIA_VIDEOVOIP("video/vnd.nokia.videovoip"),
  VND_OBJECTVIDEO("video/vnd.objectvideo"),
  VND_SEALED_MPEG1("video/vnd.sealed.mpeg1"),
  VND_SEALED_MPEG4("video/vnd.sealed.mpeg4"),
  VND_SEALED_SWF("video/vnd.sealed.swf"),
  VND_SEALEDMEDIA_SOFTSEAL_MOV("video/vnd.sealedmedia.softseal.mov"),
  VND_VIVO("video/vnd.vivo");

  private final String contentType;

  VideoMediaType(String contentType)
  {
    this.contentType = contentType;
  }

  public String toString()
  {
    return contentType;
  }
}