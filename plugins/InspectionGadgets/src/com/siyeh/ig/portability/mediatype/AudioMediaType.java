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

public enum AudioMediaType
{
  THIRTYTWO_KADPCM("audio/32kadpcm"),
  THREE_GPP("audio/3gpp"),
  THREE_GPP2("audio/3gpp2"),
  AC3("audio/ac3"),
  AMR("audio/AMR"),
  AMR_WB("audio/AMR-WB"),
  AMR_WB_("audio/amr-wb+"),
  ASC("audio/asc"),
  BASIC("audio/basic"),
  BV16("audio/BV16"),
  BV32("audio/BV32"),
  CLEARMODE("audio/clearmode"),
  CN("audio/CN"),
  DAT12("audio/DAT12"),
  DLS("audio/dls"),
  DSR_ES201108("audio/dsr-es201108"),
  DSR_ES202050("audio/dsr-es202050"),
  DSR_ES202211("audio/dsr-es202211"),
  DSR_ES202212("audio/dsr-es202212"),
  EAC3("audio/eac3"),
  DVI4("audio/DVI4"),
  EVRC("audio/EVRC"),
  EVRC0("audio/EVRC0"),
  EVRC_QCP("audio/EVRC-QCP"),
  EXAMPLE("audio/example"),
  G722("audio/G722"),
  G7221("audio/G7221"),
  G723("audio/G723"),
  G726_16("audio/G726-16"),
  G726_24("audio/G726-24"),
  G726_32("audio/G726-32"),
  G726_40("audio/G726-40"),
  G728("audio/G728"),
  G729("audio/G729"),
  G729D("audio/G729D"),
  G729E("audio/G729E"),
  GSM("audio/GSM"),
  GSM_EFR("audio/GSM-EFR"),
  ILBC("audio/iLBC"),
  L8("audio/L8"),
  L16("audio/L16"),
  L20("audio/L20"),
  L24("audio/L24"),
  LPC("audio/LPC"),
  MOBILE_XMF("audio/mobile-xmf"),
  MPA("audio/MPA"),
  MP4("audio/mp4"),
  MP4A_LATM("audio/MP4A-LATM"),
  MPA_ROBUST("audio/mpa-robust"),
  MPEG("audio/mpeg"),
  MPEG4_GENERIC("audio/mpeg4-generic"),
  PARITYFEC("audio/parityfec"),
  PCMA("audio/PCMA"),
  PCMU("audio/PCMU"),
  PRS_SID("audio/prs.sid"),
  QCELP("audio/QCELP"),
  RED("audio/RED"),
  RTP_MIDI("audio/rtp-midi"),
  RTX("audio/rtx"),
  SMV("audio/SMV"),
  SMV0("audio/SMV0"),
  SMV_QCP("audio/SMV-QCP"),
  T140C("audio/t140c"),
  T38("audio/t38"),
  TELEPHONE_EVENT("audio/telephone-event"),
  TONE("audio/tone"),
  VDVI("audio/VDVI"),
  VMR_WB("audio/VMR-WB"),
  VND_3GPP_IUFP("audio/vnd.3gpp.iufp"),
  VND_4SB("audio/vnd.4SB"),
  VND_AUDIOKOZ("audio/vnd.audiokoz"),
  VND_CELP("audio/vnd.CELP"),
  VND_CISCO_NSE("audio/vnd.cisco.nse"),
  VND_CMLES_RADIO_EVENTS("audio/vnd.cmles.radio-events"),
  VND_CNS_ANP1("audio/vnd.cns.anp1"),
  VND_CNS_INF1("audio/vnd.cns.inf1"),
  VND_DIGITAL_WINDS("audio/vnd.digital-winds"),
  VND_DLNA_ADTS("audio/vnd.dlna.adts"),
  VND_EVERAD_PLJ("audio/vnd.everad.plj"),
  VND_HNS_AUDIO("audio/vnd.hns.audio"),
  VND_LUCENT_VOICE("audio/vnd.lucent.voice"),
  VND_NOKIA_MOBILE_XMF("audio/vnd.nokia.mobile-xmf"),
  VND_NORTEL_VBK("audio/vnd.nortel.vbk"),
  VND_NUERA_ECELP4800("audio/vnd.nuera.ecelp4800"),
  VND_NUERA_ECELP7470("audio/vnd.nuera.ecelp7470"),
  VND_NUERA_ECELP9600("audio/vnd.nuera.ecelp9600"),
  VND_OCTEL_SBC("audio/vnd.octel.sbc"),
  VND_QCELP("audio/vnd.qcelp"),
  VND_RHETOREX_32KADPCM("audio/vnd.rhetorex.32kadpcm"),
  VND_SEALEDMEDIA_SOFTSEAL_MPEG("audio/vnd.sealedmedia.softseal.mpeg"),
  VND_VMX_CVSD("audio/vnd.vmx.cvsd");

  private final String contentType;

  AudioMediaType(String contentType)
  {
    this.contentType = contentType;
  }

  public String toString()
  {
    return contentType;
  }
}