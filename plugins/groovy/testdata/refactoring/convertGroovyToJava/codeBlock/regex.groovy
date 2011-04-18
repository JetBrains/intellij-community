def pattern= ~"fdhsjk"

def matcher = pattern.matcher("fdg")
matcher.matches()
print 2==~"sdf"
if ((2=~"sdf").matches()) print true