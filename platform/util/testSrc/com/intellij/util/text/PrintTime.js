var dt = new Date(parseInt(WSH.Arguments(0)));
var du = new Date(dt.getUTCFullYear(), dt.getUTCMonth(), dt.getUTCDay(), dt.getUTCHours(), dt.getUTCMinutes(), dt.getUTCSeconds(), 0);
WSH.Echo(du.toLocaleTimeString());
