class Test {
  void additiveTest(int a, int b, int c, int d) {
    int e = -(a <caret>* b);
    int f = -(a <caret>* -b);
    int g = -(a <caret>- b);
    int h = -(-a <caret>+ b);
    int i = a / (b <caret>* c);
    int j = -a / -(-b <caret>* c);
    int k = a / -(b <caret>/ -c * d);
    int l = a * -b / (c <caret>* d);
    int m = a - b / (c *<caret> -d);
    int n = a / (b *<caret> -c / d) / a;
    int o = (b *<caret> c) / a;
    int p = a - (-b <caret>- c);
    int q = a - (b +<caret> -c);
    int r = a - (b - c <caret>+ d);
    int s = -a - -(b - c <caret>+ -d);
    int t = a - (b <caret>- c) - d;
    int u = a - b - (c <caret>- d);
    int v = a - (b -<caret> -c) - d;
    int w = -(b -<caret> -c) - a;
    int x = a - (b -<caret> c / -d);
    int y = a / (b <caret>* -c);
    int z = a / (-b <caret>* c / d);
    int aa = a / (b * <caret>-c / d);
    int bb = a / (b * <caret>c / -d);
    int cc = a - b / (c * <caret>-d);
    int dd = a / (b *<caret> -c) / -d;
  }
}