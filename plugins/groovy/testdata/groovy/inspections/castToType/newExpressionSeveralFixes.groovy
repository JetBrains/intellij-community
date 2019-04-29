class C {
  C(Integer i){
  }

  C(Boolean i, Double d){
  }

  C(String s, Object o){
  }
}

def l = new C(new <caret>Object(), new Object())