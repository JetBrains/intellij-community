/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ui;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings({"InspectionUsingGrayColors", "UnusedDeclaration"})
public class Gray extends Color {
  private Gray(int num) {
    super(num, num, num);
  }

  private Gray(int num, int alpha) {
    super(num, num, num, alpha);
  }

  public Color withAlpha(int alpha) {
    if (alpha == 0) {
      return TRANSPARENT;
    }

    assert 0 <= alpha && alpha <= 255 : "Alpha " + alpha + "is incorrect. Alpha should be in range 0..255";
    return new Gray(getRed(), alpha);
  }

  public static Gray get(int gray) {
    assert 0 <= gray && gray <= 255 : "Gray == " + gray +"Gray should be in range 0..255";
    return cache[gray];
  }

  public static Color get(int gray, int alpha) {
    return get(gray).withAlpha(alpha);
  }

  public static final Gray _0 = new Gray(0);
  public static final Gray _1 = new Gray(1);
  public static final Gray _2 = new Gray(2);
  public static final Gray _3 = new Gray(3);
  public static final Gray _4 = new Gray(4);
  public static final Gray _5 = new Gray(5);
  public static final Gray _6 = new Gray(6);
  public static final Gray _7 = new Gray(7);
  public static final Gray _8 = new Gray(8);
  public static final Gray _9 = new Gray(9);
  public static final Gray _10 = new Gray(10);
  public static final Gray _11 = new Gray(11);
  public static final Gray _12 = new Gray(12);
  public static final Gray _13 = new Gray(13);
  public static final Gray _14 = new Gray(14);
  public static final Gray _15 = new Gray(15);
  public static final Gray _16 = new Gray(16);
  public static final Gray _17 = new Gray(17);
  public static final Gray _18 = new Gray(18);
  public static final Gray _19 = new Gray(19);
  public static final Gray _20 = new Gray(20);
  public static final Gray _21 = new Gray(21);
  public static final Gray _22 = new Gray(22);
  public static final Gray _23 = new Gray(23);
  public static final Gray _24 = new Gray(24);
  public static final Gray _25 = new Gray(25);
  public static final Gray _26 = new Gray(26);
  public static final Gray _27 = new Gray(27);
  public static final Gray _28 = new Gray(28);
  public static final Gray _29 = new Gray(29);
  public static final Gray _30 = new Gray(30);
  public static final Gray _31 = new Gray(31);
  public static final Gray _32 = new Gray(32);
  public static final Gray _33 = new Gray(33);
  public static final Gray _34 = new Gray(34);
  public static final Gray _36 = new Gray(36);
  public static final Gray _35 = new Gray(35);
  public static final Gray _37 = new Gray(37);
  public static final Gray _38 = new Gray(38);
  public static final Gray _39 = new Gray(39);
  public static final Gray _40 = new Gray(40);
  public static final Gray _41 = new Gray(41);
  public static final Gray _42 = new Gray(42);
  public static final Gray _43 = new Gray(43);
  public static final Gray _44 = new Gray(44);
  public static final Gray _45 = new Gray(45);
  public static final Gray _46 = new Gray(46);
  public static final Gray _47 = new Gray(47);
  public static final Gray _48 = new Gray(48);
  public static final Gray _49 = new Gray(49);
  public static final Gray _50 = new Gray(50);
  public static final Gray _51 = new Gray(51);
  public static final Gray _52 = new Gray(52);
  public static final Gray _53 = new Gray(53);
  public static final Gray _54 = new Gray(54);
  public static final Gray _55 = new Gray(55);
  public static final Gray _56 = new Gray(56);
  public static final Gray _57 = new Gray(57);
  public static final Gray _58 = new Gray(58);
  public static final Gray _59 = new Gray(59);
  public static final Gray _60 = new Gray(60);
  public static final Gray _61 = new Gray(61);
  public static final Gray _62 = new Gray(62);
  public static final Gray _63 = new Gray(63);
  public static final Gray _64 = new Gray(64);
  public static final Gray _65 = new Gray(65);
  public static final Gray _66 = new Gray(66);
  public static final Gray _67 = new Gray(67);
  public static final Gray _68 = new Gray(68);
  public static final Gray _69 = new Gray(69);
  public static final Gray _70 = new Gray(70);
  public static final Gray _71 = new Gray(71);
  public static final Gray _72 = new Gray(72);
  public static final Gray _73 = new Gray(73);
  public static final Gray _74 = new Gray(74);
  public static final Gray _75 = new Gray(75);
  public static final Gray _76 = new Gray(76);
  public static final Gray _77 = new Gray(77);
  public static final Gray _78 = new Gray(78);
  public static final Gray _79 = new Gray(79);
  public static final Gray _80 = new Gray(80);
  public static final Gray _81 = new Gray(81);
  public static final Gray _82 = new Gray(82);
  public static final Gray _83 = new Gray(83);
  public static final Gray _84 = new Gray(84);
  public static final Gray _85 = new Gray(85);
  public static final Gray _86 = new Gray(86);
  public static final Gray _87 = new Gray(87);
  public static final Gray _88 = new Gray(88);
  public static final Gray _89 = new Gray(89);
  public static final Gray _90 = new Gray(90);
  public static final Gray _91 = new Gray(91);
  public static final Gray _92 = new Gray(92);
  public static final Gray _93 = new Gray(93);
  public static final Gray _94 = new Gray(94);
  public static final Gray _95 = new Gray(95);
  public static final Gray _96 = new Gray(96);
  public static final Gray _97 = new Gray(97);
  public static final Gray _98 = new Gray(98);
  public static final Gray _99 = new Gray(99);
  public static final Gray _100 = new Gray(100);
  public static final Gray _101 = new Gray(101);
  public static final Gray _102 = new Gray(102);
  public static final Gray _103 = new Gray(103);
  public static final Gray _104 = new Gray(104);
  public static final Gray _105 = new Gray(105);
  public static final Gray _106 = new Gray(106);
  public static final Gray _107 = new Gray(107);
  public static final Gray _108 = new Gray(108);
  public static final Gray _109 = new Gray(109);
  public static final Gray _110 = new Gray(110);
  public static final Gray _111 = new Gray(111);
  public static final Gray _112 = new Gray(112);
  public static final Gray _113 = new Gray(113);
  public static final Gray _114 = new Gray(114);
  public static final Gray _115 = new Gray(115);
  public static final Gray _116 = new Gray(116);
  public static final Gray _117 = new Gray(117);
  public static final Gray _118 = new Gray(118);
  public static final Gray _119 = new Gray(119);
  public static final Gray _120 = new Gray(120);
  public static final Gray _121 = new Gray(121);
  public static final Gray _122 = new Gray(122);
  public static final Gray _123 = new Gray(123);
  public static final Gray _124 = new Gray(124);
  public static final Gray _125 = new Gray(125);
  public static final Gray _126 = new Gray(126);
  public static final Gray _127 = new Gray(127);
  public static final Gray _128 = new Gray(128);
  public static final Gray _129 = new Gray(129);
  public static final Gray _130 = new Gray(130);
  public static final Gray _131 = new Gray(131);
  public static final Gray _132 = new Gray(132);
  public static final Gray _133 = new Gray(133);
  public static final Gray _134 = new Gray(134);
  public static final Gray _135 = new Gray(135);
  public static final Gray _136 = new Gray(136);
  public static final Gray _137 = new Gray(137);
  public static final Gray _138 = new Gray(138);
  public static final Gray _139 = new Gray(139);
  public static final Gray _140 = new Gray(140);
  public static final Gray _141 = new Gray(141);
  public static final Gray _142 = new Gray(142);
  public static final Gray _143 = new Gray(143);
  public static final Gray _144 = new Gray(144);
  public static final Gray _145 = new Gray(145);
  public static final Gray _146 = new Gray(146);
  public static final Gray _147 = new Gray(147);
  public static final Gray _148 = new Gray(148);
  public static final Gray _149 = new Gray(149);
  public static final Gray _150 = new Gray(150);
  public static final Gray _151 = new Gray(151);
  public static final Gray _152 = new Gray(152);
  public static final Gray _153 = new Gray(153);
  public static final Gray _154 = new Gray(154);
  public static final Gray _155 = new Gray(155);
  public static final Gray _156 = new Gray(156);
  public static final Gray _157 = new Gray(157);
  public static final Gray _158 = new Gray(158);
  public static final Gray _159 = new Gray(159);
  public static final Gray _160 = new Gray(160);
  public static final Gray _161 = new Gray(161);
  public static final Gray _162 = new Gray(162);
  public static final Gray _163 = new Gray(163);
  public static final Gray _164 = new Gray(164);
  public static final Gray _165 = new Gray(165);
  public static final Gray _166 = new Gray(166);
  public static final Gray _167 = new Gray(167);
  public static final Gray _168 = new Gray(168);
  public static final Gray _169 = new Gray(169);
  public static final Gray _170 = new Gray(170);
  public static final Gray _171 = new Gray(171);
  public static final Gray _172 = new Gray(172);
  public static final Gray _173 = new Gray(173);
  public static final Gray _174 = new Gray(174);
  public static final Gray _175 = new Gray(175);
  public static final Gray _176 = new Gray(176);
  public static final Gray _177 = new Gray(177);
  public static final Gray _178 = new Gray(178);
  public static final Gray _179 = new Gray(179);
  public static final Gray _180 = new Gray(180);
  public static final Gray _181 = new Gray(181);
  public static final Gray _182 = new Gray(182);
  public static final Gray _183 = new Gray(183);
  public static final Gray _184 = new Gray(184);
  public static final Gray _185 = new Gray(185);
  public static final Gray _186 = new Gray(186);
  public static final Gray _187 = new Gray(187);
  public static final Gray _188 = new Gray(188);
  public static final Gray _189 = new Gray(189);
  public static final Gray _190 = new Gray(190);
  public static final Gray _191 = new Gray(191);
  public static final Gray _192 = new Gray(192);
  public static final Gray _193 = new Gray(193);
  public static final Gray _194 = new Gray(194);
  public static final Gray _195 = new Gray(195);
  public static final Gray _196 = new Gray(196);
  public static final Gray _197 = new Gray(197);
  public static final Gray _198 = new Gray(198);
  public static final Gray _199 = new Gray(199);
  public static final Gray _200 = new Gray(200);
  public static final Gray _201 = new Gray(201);
  public static final Gray _202 = new Gray(202);
  public static final Gray _203 = new Gray(203);
  public static final Gray _204 = new Gray(204);
  public static final Gray _205 = new Gray(205);
  public static final Gray _206 = new Gray(206);
  public static final Gray _207 = new Gray(207);
  public static final Gray _208 = new Gray(208);
  public static final Gray _209 = new Gray(209);
  public static final Gray _210 = new Gray(210);
  public static final Gray _211 = new Gray(211);
  public static final Gray _212 = new Gray(212);
  public static final Gray _213 = new Gray(213);
  public static final Gray _214 = new Gray(214);
  public static final Gray _215 = new Gray(215);
  public static final Gray _216 = new Gray(216);
  public static final Gray _217 = new Gray(217);
  public static final Gray _218 = new Gray(218);
  public static final Gray _219 = new Gray(219);
  public static final Gray _220 = new Gray(220);
  public static final Gray _221 = new Gray(221);
  public static final Gray _222 = new Gray(222);
  public static final Gray _223 = new Gray(223);
  public static final Gray _224 = new Gray(224);
  public static final Gray _225 = new Gray(225);
  public static final Gray _226 = new Gray(226);
  public static final Gray _227 = new Gray(227);
  public static final Gray _228 = new Gray(228);
  public static final Gray _229 = new Gray(229);
  public static final Gray _230 = new Gray(230);
  public static final Gray _231 = new Gray(231);
  public static final Gray _232 = new Gray(232);
  public static final Gray _233 = new Gray(233);
  public static final Gray _234 = new Gray(234);
  public static final Gray _235 = new Gray(235);
  public static final Gray _236 = new Gray(236);
  public static final Gray _237 = new Gray(237);
  public static final Gray _238 = new Gray(238);
  public static final Gray _239 = new Gray(239);
  public static final Gray _240 = new Gray(240);
  public static final Gray _241 = new Gray(241);
  public static final Gray _242 = new Gray(242);
  public static final Gray _243 = new Gray(243);
  public static final Gray _244 = new Gray(244);
  public static final Gray _245 = new Gray(245);
  public static final Gray _246 = new Gray(246);
  public static final Gray _247 = new Gray(247);
  public static final Gray _248 = new Gray(248);
  public static final Gray _249 = new Gray(249);
  public static final Gray _250 = new Gray(250);
  public static final Gray _251 = new Gray(251);
  public static final Gray _252 = new Gray(252);
  public static final Gray _253 = new Gray(253);
  public static final Gray _254 = new Gray(254);
  public static final Gray _255 = new Gray(255);

  public static final Gray x00 = _0;
  public static final Gray x01 = _1;
  public static final Gray x02 = _2;
  public static final Gray x03 = _3;
  public static final Gray x04 = _4;
  public static final Gray x05 = _5;
  public static final Gray x06 = _6;
  public static final Gray x07 = _7;
  public static final Gray x08 = _8;
  public static final Gray x09 = _9;
  public static final Gray x0A = _10;
  public static final Gray x0B = _11;
  public static final Gray x0C = _12;
  public static final Gray x0D = _13;
  public static final Gray x0E = _14;
  public static final Gray x0F = _15;
  public static final Gray x10 = _16;
  public static final Gray x11 = _17;
  public static final Gray x12 = _18;
  public static final Gray x13 = _19;
  public static final Gray x14 = _20;
  public static final Gray x15 = _21;
  public static final Gray x16 = _22;
  public static final Gray x17 = _23;
  public static final Gray x18 = _24;
  public static final Gray x19 = _25;
  public static final Gray x1A = _26;
  public static final Gray x1B = _27;
  public static final Gray x1C = _28;
  public static final Gray x1D = _29;
  public static final Gray x1E = _30;
  public static final Gray x1F = _31;
  public static final Gray x20 = _32;
  public static final Gray x21 = _33;
  public static final Gray x22 = _34;
  public static final Gray x23 = _35;
  public static final Gray x24 = _36;
  public static final Gray x25 = _37;
  public static final Gray x26 = _38;
  public static final Gray x27 = _39;
  public static final Gray x28 = _40;
  public static final Gray x29 = _41;
  public static final Gray x2A = _42;
  public static final Gray x2B = _43;
  public static final Gray x2C = _44;
  public static final Gray x2D = _45;
  public static final Gray x2E = _46;
  public static final Gray x2F = _47;
  public static final Gray x30 = _48;
  public static final Gray x31 = _49;
  public static final Gray x32 = _50;
  public static final Gray x33 = _51;
  public static final Gray x34 = _52;
  public static final Gray x35 = _53;
  public static final Gray x36 = _54;
  public static final Gray x37 = _55;
  public static final Gray x38 = _56;
  public static final Gray x39 = _57;
  public static final Gray x3A = _58;
  public static final Gray x3B = _59;
  public static final Gray x3C = _60;
  public static final Gray x3D = _61;
  public static final Gray x3E = _62;
  public static final Gray x3F = _63;
  public static final Gray x40 = _64;
  public static final Gray x41 = _65;
  public static final Gray x42 = _66;
  public static final Gray x43 = _67;
  public static final Gray x44 = _68;
  public static final Gray x45 = _69;
  public static final Gray x46 = _70;
  public static final Gray x47 = _71;
  public static final Gray x48 = _72;
  public static final Gray x49 = _73;
  public static final Gray x4A = _74;
  public static final Gray x4B = _75;
  public static final Gray x4C = _76;
  public static final Gray x4D = _77;
  public static final Gray x4E = _78;
  public static final Gray x4F = _79;
  public static final Gray x50 = _80;
  public static final Gray x51 = _81;
  public static final Gray x52 = _82;
  public static final Gray x53 = _83;
  public static final Gray x54 = _84;
  public static final Gray x55 = _85;
  public static final Gray x56 = _86;
  public static final Gray x57 = _87;
  public static final Gray x58 = _88;
  public static final Gray x59 = _89;
  public static final Gray x5A = _90;
  public static final Gray x5B = _91;
  public static final Gray x5C = _92;
  public static final Gray x5D = _93;
  public static final Gray x5E = _94;
  public static final Gray x5F = _95;
  public static final Gray x60 = _96;
  public static final Gray x61 = _97;
  public static final Gray x62 = _98;
  public static final Gray x63 = _99;
  public static final Gray x64 = _100;
  public static final Gray x65 = _101;
  public static final Gray x66 = _102;
  public static final Gray x67 = _103;
  public static final Gray x68 = _104;
  public static final Gray x69 = _105;
  public static final Gray x6A = _106;
  public static final Gray x6B = _107;
  public static final Gray x6C = _108;
  public static final Gray x6D = _109;
  public static final Gray x6E = _110;
  public static final Gray x6F = _111;
  public static final Gray x70 = _112;
  public static final Gray x71 = _113;
  public static final Gray x72 = _114;
  public static final Gray x73 = _115;
  public static final Gray x74 = _116;
  public static final Gray x75 = _117;
  public static final Gray x76 = _118;
  public static final Gray x77 = _119;
  public static final Gray x78 = _120;
  public static final Gray x79 = _121;
  public static final Gray x7A = _122;
  public static final Gray x7B = _123;
  public static final Gray x7C = _124;
  public static final Gray x7D = _125;
  public static final Gray x7E = _126;
  public static final Gray x7F = _127;
  public static final Gray x80 = _128;
  public static final Gray x81 = _129;
  public static final Gray x82 = _130;
  public static final Gray x83 = _131;
  public static final Gray x84 = _132;
  public static final Gray x85 = _133;
  public static final Gray x86 = _134;
  public static final Gray x87 = _135;
  public static final Gray x88 = _136;
  public static final Gray x89 = _137;
  public static final Gray x8A = _138;
  public static final Gray x8B = _139;
  public static final Gray x8C = _140;
  public static final Gray x8D = _141;
  public static final Gray x8E = _142;
  public static final Gray x8F = _143;
  public static final Gray x90 = _144;
  public static final Gray x91 = _145;
  public static final Gray x92 = _146;
  public static final Gray x93 = _147;
  public static final Gray x94 = _148;
  public static final Gray x95 = _149;
  public static final Gray x96 = _150;
  public static final Gray x97 = _151;
  public static final Gray x98 = _152;
  public static final Gray x99 = _153;
  public static final Gray x9A = _154;
  public static final Gray x9B = _155;
  public static final Gray x9C = _156;
  public static final Gray x9D = _157;
  public static final Gray x9E = _158;
  public static final Gray x9F = _159;
  public static final Gray xA0 = _160;
  public static final Gray xA1 = _161;
  public static final Gray xA2 = _162;
  public static final Gray xA3 = _163;
  public static final Gray xA4 = _164;
  public static final Gray xA5 = _165;
  public static final Gray xA6 = _166;
  public static final Gray xA7 = _167;
  public static final Gray xA8 = _168;
  public static final Gray xA9 = _169;
  public static final Gray xAA = _170;
  public static final Gray xAB = _171;
  public static final Gray xAC = _172;
  public static final Gray xAD = _173;
  public static final Gray xAE = _174;
  public static final Gray xAF = _175;
  public static final Gray xB0 = _176;
  public static final Gray xB1 = _177;
  public static final Gray xB2 = _178;
  public static final Gray xB3 = _179;
  public static final Gray xB4 = _180;
  public static final Gray xB5 = _181;
  public static final Gray xB6 = _182;
  public static final Gray xB7 = _183;
  public static final Gray xB8 = _184;
  public static final Gray xB9 = _185;
  public static final Gray xBA = _186;
  public static final Gray xBB = _187;
  public static final Gray xBC = _188;
  public static final Gray xBD = _189;
  public static final Gray xBE = _190;
  public static final Gray xBF = _191;
  public static final Gray xC0 = _192;
  public static final Gray xC1 = _193;
  public static final Gray xC2 = _194;
  public static final Gray xC3 = _195;
  public static final Gray xC4 = _196;
  public static final Gray xC5 = _197;
  public static final Gray xC6 = _198;
  public static final Gray xC7 = _199;
  public static final Gray xC8 = _200;
  public static final Gray xC9 = _201;
  public static final Gray xCA = _202;
  public static final Gray xCB = _203;
  public static final Gray xCC = _204;
  public static final Gray xCD = _205;
  public static final Gray xCE = _206;
  public static final Gray xCF = _207;
  public static final Gray xD0 = _208;
  public static final Gray xD1 = _209;
  public static final Gray xD2 = _210;
  public static final Gray xD3 = _211;
  public static final Gray xD4 = _212;
  public static final Gray xD5 = _213;
  public static final Gray xD6 = _214;
  public static final Gray xD7 = _215;
  public static final Gray xD8 = _216;
  public static final Gray xD9 = _217;
  public static final Gray xDA = _218;
  public static final Gray xDB = _219;
  public static final Gray xDC = _220;
  public static final Gray xDD = _221;
  public static final Gray xDE = _222;
  public static final Gray xDF = _223;
  public static final Gray xE0 = _224;
  public static final Gray xE1 = _225;
  public static final Gray xE2 = _226;
  public static final Gray xE3 = _227;
  public static final Gray xE4 = _228;
  public static final Gray xE5 = _229;
  public static final Gray xE6 = _230;
  public static final Gray xE7 = _231;
  public static final Gray xE8 = _232;
  public static final Gray xE9 = _233;
  public static final Gray xEA = _234;
  public static final Gray xEB = _235;
  public static final Gray xEC = _236;
  public static final Gray xED = _237;
  public static final Gray xEE = _238;
  public static final Gray xEF = _239;
  public static final Gray xF0 = _240;
  public static final Gray xF1 = _241;
  public static final Gray xF2 = _242;
  public static final Gray xF3 = _243;
  public static final Gray xF4 = _244;
  public static final Gray xF5 = _245;
  public static final Gray xF6 = _246;
  public static final Gray xF7 = _247;
  public static final Gray xF8 = _248;
  public static final Gray xF9 = _249;
  public static final Gray xFA = _250;
  public static final Gray xFB = _251;
  public static final Gray xFC = _252;
  public static final Gray xFD = _253;
  public static final Gray xFE = _254;
  public static final Gray xFF = _255;

  private static final Gray[] cache = {
      _0,   _1,   _2,   _3,   _4,   _5,   _6,   _7,   _8,   _9,  _10,  _11,  _12,  _13,  _14,  _15,
     _16,  _17,  _18,  _19,  _20,  _21,  _22,  _23,  _24,  _25,  _26,  _27,  _28,  _29,  _30,  _31,
     _32,  _33,  _34,  _35,  _36,  _37,  _38,  _39,  _40,  _41,  _42,  _43,  _44,  _45,  _46,  _47,
     _48,  _49,  _50,  _51,  _52,  _53,  _54,  _55,  _56,  _57,  _58,  _59,  _60,  _61,  _62,  _63,
     _64,  _65,  _66,  _67,  _68,  _69,  _70,  _71,  _72,  _73,  _74,  _75,  _76,  _77,  _78,  _79,
     _80,  _81,  _82,  _83,  _84,  _85,  _86,  _87,  _88,  _89,  _90,  _91,  _92,  _93,  _94,  _95,
     _96,  _97,  _98,  _99, _100, _101, _102, _103, _104, _105, _106, _107, _108, _109, _110, _111,
    _112, _113, _114, _115, _116, _117, _118, _119, _120, _121, _122, _123, _124, _125, _126, _127,
    _128, _129, _130, _131, _132, _133, _134, _135, _136, _137, _138, _139, _140, _141, _142, _143,
    _144, _145, _146, _147, _148, _149, _150, _151, _152, _153, _154, _155, _156, _157, _158, _159,
    _160, _161, _162, _163, _164, _165, _166, _167, _168, _169, _170, _171, _172, _173, _174, _175,
    _176, _177, _178, _179, _180, _181, _182, _183, _184, _185, _186, _187, _188, _189, _190, _191,
    _192, _193, _194, _195, _196, _197, _198, _199, _200, _201, _202, _203, _204, _205, _206, _207,
    _208, _209, _210, _211, _212, _213, _214, _215, _216, _217, _218, _219, _220, _221, _222, _223,
    _224, _225, _226, _227, _228, _229, _230, _231, _232, _233, _234, _235, _236, _237, _238, _239,
    _240, _241, _242, _243, _244, _245, _246, _247, _248, _249, _250, _251, _252, _253, _254, _255};

  @SuppressWarnings("UseJBColor")
  public static final Color TRANSPARENT = new Color(0,0,0,0);

  //public static void main(String[] args) {
  //  for (int i = 0; i < 256; i++) {
  //    System.out.println("public static final Gray _" + i + " = new Gray("+ i + ");");
  //  }
  //  for (int i = 0; i < 256; i++) {
  //    System.out.println("public static final Gray x" + (i < 16 ? "0" : "") + Integer.toHexString(i).toUpperCase() + " = _" + i + ";");
  //  }
  //
  //  System.out.println();
  //  System.out.println("private static final Gray[] cache = {");
  //  System.out.print("  ");
  //  for (int i = 0; i < 256; i++) {
  //    System.out.print(String.format("%4s", "_" + String.valueOf(i)));
  //    if (i == 255) {
  //      System.out.println("};");
  //    } else {
  //      if (i % 16 == 15) {
  //        System.out.println(",");
  //        System.out.print("  ");
  //      } else {
  //        System.out.print(", ");
  //      }
  //    }
  //  }
  //}
}
