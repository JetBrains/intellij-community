Upper upper = new Upper();
println(((Test)upper.getTest()).setBar(((Bar)((Test)upper.getTest()).getBar()).next()));
Test test = new Test();
test.state = test.state.next();
print(test);
