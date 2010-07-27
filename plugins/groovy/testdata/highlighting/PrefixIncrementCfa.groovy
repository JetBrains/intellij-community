int idx
idx = <warning descr="Assignment is not used">2</warning>
idx = 3
if (++idx == 8) {  //Assignment is used here
  idx = 33
}
print idx