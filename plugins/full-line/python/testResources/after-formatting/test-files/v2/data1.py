import json
import logging
from flask import Flask
from flask_cors import CORS
from flask_injector import FlaskInjector
from gevent.pywsgi import WSGIServer
from module import AppModule
from src.application.v1.api import api
app = Flask(__name__)
CORS(app)
with open("config.json") as f:⇥config = json.load(f)⇤app.config.update(config)
logging.basicConfig(format="%(asctime)s\t:\t%(levelname)s\t:\t%(name)s\t:\t%(message)s")
logging.getLogger().setLevel(logging.DEBUG)
logging.getLogger("pytorch_transformers.tokenization_utils").disabled = True
logging.getLogger("urllib3.connectionpool").disabled = True
app.register_blueprint(api, url_prefix="/v1")
FlaskInjector(app=app, modules=[AppModule])
def nginx(_config):⇥if _config["debug"]:⇥logging.getLogger().setLevel(logging.DEBUG)⇤else:⇥logging.getLogger().setLevel(logging.INFO)⇤if config["processes"] > 0:⇥app.processes = config["processes"]⇤if config["processes"] > 0:⇥app.processes = config["processes"]⇤http_server = WSGIServer(("", 8001), app)
logging.info("Server is running on Nginx correctly!")
http_server.serve_forever()⇤def default(_config):⇥app.run(⇥host=_config["host"], port=_config["port"], debug=_config["debug"], use_evalex=_config["interactive_debugger"]⇤)⇤if __name__ == "__main__":⇥_config = app.config["run"]
if _config["ngi