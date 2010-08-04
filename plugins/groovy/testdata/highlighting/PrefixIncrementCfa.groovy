int idx
<warning descr="Assignment is not used">idx</warning> = 2
idx = 3
if (++idx == 8) {  //Assignment is used here
  idx = 33
}
print idx