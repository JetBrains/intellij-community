def r(String s) {}
def r(int i) {}

def ttt(){
  def t

  t = ""
  <caret>r(t)
  t = 0
  r(t)
}