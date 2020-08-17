def ok() {
  print 1
  print 2
  print 3
  print 4
  print 5
}

def <warning descr="Method 'foo' is too long (statement count = 6>5)">foo</warning>() {
  print 1
  print 2
  print 3
  print 4
  print 5
  print 6
}

def <warning descr="Method 'methodCalls' is too long (statement count = 7>5)">methodCalls</warning>() {
  print(1)
  print(2)
  print(3)
  print(4)
  print(5)
  print(6)
  print(7)
}

def <warning descr="Method 'whileStatement' is too long (statement count = 6>5)">whileStatement</warning>() {
  while (x) {
    print 1
    print 2
    print 3
    print 4
    print 5
  }
}

def anonymousClass() {
  new Runnable() {
    void <warning descr="Method 'run' is too long (statement count = 6>5)">run</warning>() {
  print 1;
  print 2;
  print 3;
  print 4;
  print 5;
  print 6;
}
}
}

def <warning descr="Method 'closure' is too long (statement count = 6>5)">closure</warning>() {
  [].each {
    print 1
    print 2
    print 3
    print 4
    print 5
  }
}
