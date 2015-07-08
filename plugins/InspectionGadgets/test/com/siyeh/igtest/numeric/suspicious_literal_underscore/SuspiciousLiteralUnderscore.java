class SuspiciousLiteralUnderscore {{

  double d  = <warning descr="Group in number literal with underscores does not have length 3">1234</warning>.<warning descr="Group in number literal with underscores does not have length 3">9</warning>_8;
  float a = <warning descr="Group in number literal with underscores does not have length 3">3333</warning>.141_<warning descr="Group in number literal with underscores does not have length 3">5982</warning>_<warning descr="Group in number literal with underscores does not have length 3">6555</warning>f;
  float pi = 3.<warning descr="Group in number literal with underscores does not have length 3">14</warning>_159_265_4f;
  float pi2 = 3.141_592_654f;
  double f = 0.333_<warning descr="Group in number literal with underscores does not have length 3">3333</warning>_333;
  int i = 1_000_<warning descr="Group in number literal with underscores does not have length 3">00</warning>;
  int j = 1_000_<warning descr="Group in number literal with underscores does not have length 3">0000</warning>;
  long k = 1_000_000L;
  double g = 12_<warning descr="Group in number literal with underscores does not have length 3">34</warning>d;
  double h = 1.123_<warning descr="Group in number literal with underscores does not have length 3">4567</warning>e3;

  int hex = 0xCAFE_BABE;
  int binary = 0b11111110_01111111;
}}