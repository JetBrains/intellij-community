class Base extends Exception{}

class X extends Base{}
class Y extends Base{}

try {
  throw new X()
}
catch (X | Y ee){print e<ref>e}

