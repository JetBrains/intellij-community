package somePackage

import unrelatedPackage.Target2 as Target
//import unrelatedPackage.Target

import static unrelatedPackage.Container.Target
import static unrelatedPackage.Container.Target2 as Target

import static unrelatedPackage.Container2.Target as Unrelated2
import static unrelatedPackage.Container.Target as Unrelated

import anotherUnrelatedPackage.*
import unrelatedPackage.*

import static unrelatedPackage.Container.*
import static unrelatedPackage.Container2.*

//class Target {}

interface CurrentI {
//  static class Target {}
}

class CurrentParent extends CurrentParentOutside implements CurrentParentI, CurrentParentIOutside {
//  static class Target {}
}

interface CurrentParentI {
//  static class Target {}
}

class OuterParent extends OuterParentOutside {
  static class Target {}
}

interface OuterI {
//  static class Target {}
}

interface OuterOuterI {
//  static class Target {}
}

class OuterOuter implements OuterOuterI {

//  static class Target {}

  static class Outer extends OuterParent implements OuterI, OuterIOutside {

//    static class Target {}

    static class Current extends CurrentParent implements CurrentI, CurrentIOutside {

//      static class Target {}

      static class Inner {
        static class Target {}
      }

      static usage() {
        new <caret>Target()
      }
    }
  }
}

class Main {
  static void main(String[] args) {
    println OuterOuter.Outer.Current.usage()
  }
}
