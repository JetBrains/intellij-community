var dt = new Date();
dt.setTime(WSH.Arguments(0));
WSH.Echo(dt.toLocaleTimeString());
