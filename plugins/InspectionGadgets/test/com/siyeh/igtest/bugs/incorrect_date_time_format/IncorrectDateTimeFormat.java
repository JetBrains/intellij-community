import java.time.format.DateTimeFormatter;

class Test {
  public static final String TT = "T" + "T";
  public static final String WITH_QUOTE = "'T''";

  void test(boolean b) {
    DateTimeFormatter.ofPattern(<warning descr="Illegal pattern letter 'T'">TT</warning>);
    DateTimeFormatter.ofPattern(<warning descr="Opening single quote (') without following closing single quote">WITH_QUOTE</warning>);
    DateTimeFormatter.ofPattern("<warning descr="Illegal pattern letter 'b'">bb</warning>");
    DateTimeFormatter.ofPattern("<warning descr="Illegal pattern letter 'b'">b</warning>");
    DateTimeFormatter.ofPattern("<warning descr="Illegal pattern letter 'b'">bb</warning>-");
    DateTimeFormatter.ofPattern("<warning descr="Illegal pattern letter 'b'">bb</warning>MM<warning descr="Illegal pattern letter 't'">tt</warning>");

    DateTimeFormatter.ofPattern("dd-MM-yyyy'T'hh:mm:ss");
    DateTimeFormatter.ofPattern("dd-MM-yyyy'''T'''hh:mm:ss<warning descr="Opening single quote (') without following closing single quote">'</warning>");
    DateTimeFormatter.ofPattern("dd-MM-yyyy'''T'''hh:mm:ss''");
    DateTimeFormatter.ofPattern("dd-MM-yyyy''''<warning descr="Illegal pattern letter 'T'">T</warning>''<warning descr="Opening single quote (') without following closing single quote">'</warning>hh:mm:ss");
    DateTimeFormatter.ofPattern("dd-MM-yyyy''''<warning descr="Illegal pattern letter 'T'">T</warning>''''hh:mm:ss");
    DateTimeFormatter.ofPattern("dd-MM-yyyy<warning descr="Opening single quote (') without following closing single quote">'</warning>a");
    DateTimeFormatter.ofPattern("<warning descr="Opening single quote (') without following closing single quote">'</warning>dd-MM-yyyya");
    DateTimeFormatter.ofPattern("dd-MM-yyyya<warning descr="Opening single quote (') without following closing single quote">'</warning>");
    DateTimeFormatter.ofPattern("dd-MM-yyyy''a");
    DateTimeFormatter.ofPattern("dd-MM-yyyy'{'a");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'd'; maximum allowed: 2; amount specified: 3">ddd</warning>-MM-yyyy'{'a");
    DateTimeFormatter.ofPattern("dd-<warning descr="Too many consecutive pattern letters 'd'; maximum allowed: 2; amount specified: 3">ddd</warning>-yyyy'{'a");
    DateTimeFormatter.ofPattern("dd-dd-<warning descr="Too many consecutive pattern letters 'd'; maximum allowed: 2; amount specified: 3">ddd</warning>");

    DateTimeFormatter.ofPattern("dd-MM-yyyy<warning descr="Use of reserved character '{'">{</warning>a");
    DateTimeFormatter.ofPattern("dd-MM-yyyy<warning descr="Use of reserved character '}'">}</warning>a");
    DateTimeFormatter.ofPattern("dd-MM-yyyy&a");
    DateTimeFormatter.ofPattern("dd-MM-yyyy<warning descr="Use of reserved character '#'">#</warning>a");
    DateTimeFormatter.ofPattern("dd-MM'QQQ'-yyyy");
    DateTimeFormatter.ofPattern("dd-MM'QQQ'MMMMM-yyyy");
    DateTimeFormatter.ofPattern("\u6F22");

    DateTimeFormatter.ofPattern("<warning descr="Use of reserved character '{'">{</warning>");
    DateTimeFormatter.ofPattern("<warning descr="Use of reserved character '}'">}</warning>");
    DateTimeFormatter.ofPattern("<warning descr="Use of reserved character '#'">#</warning>");

    DateTimeFormatter.ofPattern("<warning descr="Padding modifier 'p' without consecutive pattern letters">p</warning>");
    DateTimeFormatter.ofPattern("<warning descr="Padding modifier 'p' without consecutive pattern letters">p</warning>-");
    DateTimeFormatter.ofPattern("pMM");
    DateTimeFormatter.ofPattern("pMM<warning descr="Padding modifier 'p' without consecutive pattern letters">p</warning>");
    DateTimeFormatter.ofPattern("pMMpM");
    DateTimeFormatter.ofPattern("pppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppppMMpM");
    DateTimeFormatter.ofPattern("p<warning descr="Illegal pattern letter 'T'">TT</warning>");

    DateTimeFormatter.ofPattern("[MMMMM[MMMMM[MMMMM]MMMMM[MMMMM]MMMMM]MMMMM]MMMMM<warning descr="Closing ']' without previous opening '['">]</warning>");
    DateTimeFormatter.ofPattern("[MMMMM[MMMMM[MMMMM]MMMMM[MMMMM]MMMMM]");
    DateTimeFormatter.ofPattern("[[[");
    DateTimeFormatter.ofPattern("[[[]");
    DateTimeFormatter.ofPattern("<warning descr="Closing ']' without previous opening '['">]</warning>[");
    DateTimeFormatter.ofPattern("<warning descr="Closing ']' without previous opening '['">]</warning><warning descr="Closing ']' without previous opening '['">]</warning><warning descr="Closing ']' without previous opening '['">]</warning>[]<warning descr="Closing ']' without previous opening '['">]</warning>[]");
    DateTimeFormatter.ofPattern("[[[]]]<warning descr="Closing ']' without previous opening '['">]</warning>");

    DateTimeFormatter.ofPattern("GGGGG");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'G'; maximum allowed: 5; amount specified: 6">GGGGGG</warning>");
    DateTimeFormatter.ofPattern("uuuuuuuuuuuuuuuuuuu");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'u'; maximum allowed: 19; amount specified: 20">uuuuuuuuuuuuuuuuuuuu</warning>");
    DateTimeFormatter.ofPattern("YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY");
    DateTimeFormatter.ofPattern("yyyyyyyyyyyyyyyyyyy");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'y'; maximum allowed: 19; amount specified: 20">yyyyyyyyyyyyyyyyyyyy</warning>");
    DateTimeFormatter.ofPattern("QQQQQ");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'Q'; maximum allowed: 5; amount specified: 6">QQQQQQ</warning>");
    DateTimeFormatter.ofPattern("qqqqq");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'q'; maximum allowed: 5; amount specified: 6">qqqqqq</warning>");
    DateTimeFormatter.ofPattern("MMMMM");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'M'; maximum allowed: 5; amount specified: 6">MMMMMM</warning>");
    DateTimeFormatter.ofPattern("LLLLL");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'L'; maximum allowed: 5; amount specified: 6">LLLLLL</warning>");
    DateTimeFormatter.ofPattern("ww");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'w'; maximum allowed: 2; amount specified: 3">www</warning>");
    DateTimeFormatter.ofPattern("W");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'W'; maximum allowed: 1; amount specified: 2">WW</warning>");
    DateTimeFormatter.ofPattern("dd");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'd'; maximum allowed: 2; amount specified: 3">ddd</warning>");
    DateTimeFormatter.ofPattern("DDD");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'D'; maximum allowed: 3; amount specified: 4">DDDD</warning>");
    DateTimeFormatter.ofPattern("F");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'F'; maximum allowed: 1; amount specified: 2">FF</warning>");
    DateTimeFormatter.ofPattern("ggggggggggggggggggg");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'g'; maximum allowed: 19; amount specified: 20">gggggggggggggggggggg</warning>");
    DateTimeFormatter.ofPattern("EEEEE");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'E'; maximum allowed: 5; amount specified: 6">EEEEEE</warning>");
    DateTimeFormatter.ofPattern("eeeee");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'e'; maximum allowed: 5; amount specified: 6">eeeeee</warning>");
    DateTimeFormatter.ofPattern("ccccc");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'c'; maximum allowed: 5; amount specified: 6">cccccc</warning>");

    DateTimeFormatter.ofPattern("a");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'a'; maximum allowed: 1; amount specified: 2">aa</warning>");
    DateTimeFormatter.ofPattern("hh");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'h'; maximum allowed: 2; amount specified: 3">hhh</warning>");
    DateTimeFormatter.ofPattern("HH");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'H'; maximum allowed: 2; amount specified: 3">HHH</warning>");
    DateTimeFormatter.ofPattern("kk");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'k'; maximum allowed: 2; amount specified: 3">kkk</warning>");
    DateTimeFormatter.ofPattern("mm");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'm'; maximum allowed: 2; amount specified: 3">mmm</warning>");
    DateTimeFormatter.ofPattern("ss");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 's'; maximum allowed: 2; amount specified: 3">sss</warning>");
    DateTimeFormatter.ofPattern("SSSSSSSSS");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'S'; maximum allowed: 9; amount specified: 10">SSSSSSSSSS</warning>");
    DateTimeFormatter.ofPattern("AAAAAAAAAAAAAAAAAAA");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'A'; maximum allowed: 19; amount specified: 20">AAAAAAAAAAAAAAAAAAAA</warning>");
    DateTimeFormatter.ofPattern("nnnnnnnnnnnnnnnnnnn");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'n'; maximum allowed: 19; amount specified: 20">nnnnnnnnnnnnnnnnnnnn</warning>");
    DateTimeFormatter.ofPattern("NNNNNNNNNNNNNNNNNNN");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'N'; maximum allowed: 19; amount specified: 20">NNNNNNNNNNNNNNNNNNNN</warning>");

    DateTimeFormatter.ofPattern("B");
    DateTimeFormatter.ofPattern("<warning descr="Wrong number of consecutive pattern letters 'B'; amounts allowed: 1, 4, 5; amount specified: 2">BB</warning>");
    DateTimeFormatter.ofPattern("<warning descr="Wrong number of consecutive pattern letters 'B'; amounts allowed: 1, 4, 5; amount specified: 3">BBB</warning>");
    DateTimeFormatter.ofPattern("BBBB");
    DateTimeFormatter.ofPattern("BBBBB");

    DateTimeFormatter.ofPattern("<warning descr="Too few consecutive pattern letters 'V'; minimum allowed: 2; amount specified: 1">V</warning>");
    DateTimeFormatter.ofPattern("VV");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'V'; maximum allowed: 2; amount specified: 3">VVV</warning>");
    DateTimeFormatter.ofPattern("v");
    DateTimeFormatter.ofPattern("<warning descr="Wrong number of consecutive pattern letters 'v'; amounts allowed: 1, 4; amount specified: 3">vvv</warning>");
    DateTimeFormatter.ofPattern("vvvv");
    DateTimeFormatter.ofPattern("z");
    DateTimeFormatter.ofPattern("zzzz");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'z'; maximum allowed: 4; amount specified: 5">zzzzz</warning>");

    DateTimeFormatter.ofPattern("O");
    DateTimeFormatter.ofPattern("<warning descr="Wrong number of consecutive pattern letters 'O'; amounts allowed: 1, 4; amount specified: 3">OOO</warning>");
    DateTimeFormatter.ofPattern("OOOO");
    DateTimeFormatter.ofPattern("XXXXX");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'X'; maximum allowed: 5; amount specified: 6">XXXXXX</warning>");
    DateTimeFormatter.ofPattern("xxxxx");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'x'; maximum allowed: 5; amount specified: 6">xxxxxx</warning>");
    DateTimeFormatter.ofPattern("ZZZZZ");
    DateTimeFormatter.ofPattern("<warning descr="Too many consecutive pattern letters 'Z'; maximum allowed: 5; amount specified: 6">ZZZZZZ</warning>");
  }
}