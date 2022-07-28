// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import java.time.format.DateTimeFormatter;

class Test {
  public static final String TT = "T" + "T";

  void test(boolean b) {
    DateTimeFormatter.ofPattern(<warning descr="Unsupported token: 'TT'">TT</warning>);
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'bb'">bb</warning>");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'b'">b</warning>");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'bb'">bb</warning>-");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'bb'">bb</warning>MM<warning descr="Unsupported token: 'tt'">tt</warning>");

    DateTimeFormatter.ofPattern("dd-MM-yyyy'T'hh:mm:ss");
    DateTimeFormatter.ofPattern("dd-MM-yyyy'''T'''hh:mm:ss<warning descr="Character without a pair: '''">'</warning>");
    DateTimeFormatter.ofPattern("dd-MM-yyyy'''T'''hh:mm:ss''");
    DateTimeFormatter.ofPattern("dd-MM-yyyy''''<warning descr="Unsupported token: 'T'">T</warning>''<warning descr="Character without a pair: '''">'</warning>hh:mm:ss");
    DateTimeFormatter.ofPattern("dd-MM-yyyy''''<warning descr="Unsupported token: 'T'">T</warning>''''hh:mm:ss");
    DateTimeFormatter.ofPattern("dd-MM-yyyy<warning descr="Character without a pair: '''">'</warning>a");
    DateTimeFormatter.ofPattern("<warning descr="Character without a pair: '''">'</warning>dd-MM-yyyya");
    DateTimeFormatter.ofPattern("dd-MM-yyyya<warning descr="Character without a pair: '''">'</warning>");
    DateTimeFormatter.ofPattern("dd-MM-yyyy''a");
    DateTimeFormatter.ofPattern("dd-MM-yyyy'{'a");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'ddd'">ddd</warning>-MM-yyyy'{'a");
    DateTimeFormatter.ofPattern("dd-<warning descr="Unsupported token: 'ddd'">ddd</warning>-yyyy'{'a");
    DateTimeFormatter.ofPattern("dd-dd-<warning descr="Unsupported token: 'ddd'">ddd</warning>");

    DateTimeFormatter.ofPattern("dd-MM-yyyy<warning descr="Unsupported token: '{'">{</warning>a");
    DateTimeFormatter.ofPattern("dd-MM-yyyy<warning descr="Unsupported token: '}'">}</warning>a");
    DateTimeFormatter.ofPattern("dd-MM-yyyy&a");
    DateTimeFormatter.ofPattern("dd-MM-yyyy<warning descr="Unsupported token: '#'">#</warning>a");
    DateTimeFormatter.ofPattern("dd-MM'QQQ'-yyyy");
    DateTimeFormatter.ofPattern("dd-MM'QQQ'MMMMM-yyyy");

    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: '{'">{</warning>");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: '}'">}</warning>");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: '#'">#</warning>");

    DateTimeFormatter.ofPattern("<warning descr="Not found pattern for padding">p</warning>");
    DateTimeFormatter.ofPattern("<warning descr="Not found pattern for padding">p</warning>-");
    DateTimeFormatter.ofPattern("pMM");
    DateTimeFormatter.ofPattern("pMM<warning descr="Not found pattern for padding">p</warning>");
    DateTimeFormatter.ofPattern("pMMpM");
    DateTimeFormatter.ofPattern("pppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppMMpM");
    DateTimeFormatter.ofPattern("p<warning descr="Unsupported token: 'TT'">TT</warning>");

    DateTimeFormatter.ofPattern(<warning descr="Character without a pair: ']'">"[MMMMM[MMMMM[MMMMM]MMMMM[MMMMM]MMMMM]MMMMM]MMMMM]"</warning>);
    DateTimeFormatter.ofPattern("[MMMMM[MMMMM[MMMMM]MMMMM[MMMMM]MMMMM]");
    DateTimeFormatter.ofPattern("[[[");
    DateTimeFormatter.ofPattern("[[[]");
    DateTimeFormatter.ofPattern(<warning descr="Character without a pair: ']'">"[[[]]]]"</warning>);

    DateTimeFormatter.ofPattern("GGGGG");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'GGGGGG'">GGGGGG</warning>");
    DateTimeFormatter.ofPattern("uuuuuuuuuuuuuuuuuuu");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'uuuuuuuuuuuuuuuuuuuu'">uuuuuuuuuuuuuuuuuuuu</warning>");
    DateTimeFormatter.ofPattern("YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY");
    DateTimeFormatter.ofPattern("yyyyyyyyyyyyyyyyyyy");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'yyyyyyyyyyyyyyyyyyyy'">yyyyyyyyyyyyyyyyyyyy</warning>");
    DateTimeFormatter.ofPattern("QQQQQ");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'QQQQQQ'">QQQQQQ</warning>");
    DateTimeFormatter.ofPattern("qqqqq");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'qqqqqq'">qqqqqq</warning>");
    DateTimeFormatter.ofPattern("MMMMM");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'MMMMMM'">MMMMMM</warning>");
    DateTimeFormatter.ofPattern("LLLLL");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'LLLLLL'">LLLLLL</warning>");
    DateTimeFormatter.ofPattern("ww");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'www'">www</warning>");
    DateTimeFormatter.ofPattern("W");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'WW'">WW</warning>");
    DateTimeFormatter.ofPattern("dd");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'ddd'">ddd</warning>");
    DateTimeFormatter.ofPattern("DDD");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'DDDD'">DDDD</warning>");
    DateTimeFormatter.ofPattern("F");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'FF'">FF</warning>");
    DateTimeFormatter.ofPattern("ggggggggggggggggggg");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'gggggggggggggggggggg'">gggggggggggggggggggg</warning>");
    DateTimeFormatter.ofPattern("EEEEE");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'EEEEEE'">EEEEEE</warning>");
    DateTimeFormatter.ofPattern("eeeee");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'eeeeee'">eeeeee</warning>");
    DateTimeFormatter.ofPattern("ccccc");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'cccccc'">cccccc</warning>");

    DateTimeFormatter.ofPattern("a");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'aa'">aa</warning>");
    DateTimeFormatter.ofPattern("hh");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'hhh'">hhh</warning>");
    DateTimeFormatter.ofPattern("HH");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'HHH'">HHH</warning>");
    DateTimeFormatter.ofPattern("kk");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'kkk'">kkk</warning>");
    DateTimeFormatter.ofPattern("mm");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'mmm'">mmm</warning>");
    DateTimeFormatter.ofPattern("ss");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'sss'">sss</warning>");
    DateTimeFormatter.ofPattern("SSSSSSSSS");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'SSSSSSSSSS'">SSSSSSSSSS</warning>");
    DateTimeFormatter.ofPattern("AAAAAAAAAAAAAAAAAAA");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'AAAAAAAAAAAAAAAAAAAA'">AAAAAAAAAAAAAAAAAAAA</warning>");
    DateTimeFormatter.ofPattern("nnnnnnnnnnnnnnnnnnn");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'nnnnnnnnnnnnnnnnnnnn'">nnnnnnnnnnnnnnnnnnnn</warning>");
    DateTimeFormatter.ofPattern("NNNNNNNNNNNNNNNNNNN");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'NNNNNNNNNNNNNNNNNNNN'">NNNNNNNNNNNNNNNNNNNN</warning>");

    DateTimeFormatter.ofPattern("B");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'BB'">BB</warning>");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'BBB'">BBB</warning>");
    DateTimeFormatter.ofPattern("BBBB");
    DateTimeFormatter.ofPattern("BBBBB");

    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'V'">V</warning>");
    DateTimeFormatter.ofPattern("VV");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'VVV'">VVV</warning>");
    DateTimeFormatter.ofPattern("v");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'vvv'">vvv</warning>");
    DateTimeFormatter.ofPattern("vvvv");
    DateTimeFormatter.ofPattern("z");
    DateTimeFormatter.ofPattern("zzzz");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'zzzzz'">zzzzz</warning>");

    DateTimeFormatter.ofPattern("O");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'OOO'">OOO</warning>");
    DateTimeFormatter.ofPattern("OOOO");
    DateTimeFormatter.ofPattern("XXXXX");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'XXXXXX'">XXXXXX</warning>");
    DateTimeFormatter.ofPattern("xxxxx");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'xxxxxx'">xxxxxx</warning>");
    DateTimeFormatter.ofPattern("ZZZZZ");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: 'ZZZZZZ'">ZZZZZZ</warning>");
  }
}